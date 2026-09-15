package com.kblite.chat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kblite.chat.agent.KnowledgePrompts;
import com.kblite.chat.dto.ChatRequest;
import com.kblite.chat.mapper.AgentMessageMapper;
import com.kblite.chat.memory.PersistentChatMemoryStore;
import com.kblite.chat.tools.KnowledgeTools;
import com.kblite.chat.tools.SearchContext;
import com.kblite.security.AgentRateLimiter;
import com.kblite.security.OutputSanitizer;
import com.kblite.security.PromptGuard;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.service.tool.DefaultToolExecutor;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 知识库问答对话服务（SSE 真流式）
 * 单流式模型同时处理工具调用与最终文本生成，工具执行后递归调用（最多 5 轮）
 *
 * SSE 事件：session / status / thinking / token / tool_call / tool_result /
 * sources / complete / error / heartbeat
 *
 * @author kb-agent-lite
 */
@Slf4j
@Service
public class ChatService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final int MAX_ITERATIONS = 5;
    private static final long CHAIN_TIMEOUT_MS = 170_000;
    private static final long SSE_TIMEOUT_MS = 180_000;

    @Resource
    private PersistentChatMemoryStore chatMemoryStore;
    @Resource
    private SessionService sessionService;
    @Resource
    private AgentMessageMapper agentMessageMapper;
    @Resource
    private KnowledgeTools knowledgeTools;
    @Resource
    private OpenAiStreamingChatModel streamingChatModel;
    @Resource
    private PromptGuard promptGuard;
    @Resource
    private OutputSanitizer outputSanitizer;
    @Resource
    private AgentRateLimiter agentRateLimiter;

    /**
     * 统一对话入口（SSE 流式）
     */
    public SseEmitter chatStream(ChatRequest request, Long userId) {
        // ========== 安全层：频率限制 ==========
        AgentRateLimiter.RateLimitResult rateLimitResult = agentRateLimiter.check(userId);
        if (!rateLimitResult.isAllowed()) {
            return blockedEmitter(rateLimitResult.getDenyMessage());
        }

        // ========== 安全层：输入防火墙 ==========
        String rawMessage = request.getMessage().trim();
        PromptGuard.GuardResult guardResult = promptGuard.check(rawMessage, userId);
        if (!guardResult.isPassed()) {
            return blockedEmitter(guardResult.getBlockReason());
        }
        String message = guardResult.getMessage();

        String sessionId = resolveSessionId(request.getSessionId(), userId);
        // 动态标题（最后一句用户消息）+ 刷新会话活跃时间
        sessionService.touchWithDynamicTitle(sessionId, message);

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        sendEvent(emitter, "{\"type\":\"session\",\"sessionId\":\"" + escapeJson(sessionId) + "\"}");

        AtomicBoolean emitterCompleted = new AtomicBoolean(false);
        ScheduledExecutorService heartbeatScheduler = startHeartbeat(emitter, sessionId, emitterCompleted);
        AtomicBoolean heartbeatActive = new AtomicBoolean(true);

        CompletableFuture.runAsync(() -> {
            try {
                handleStreaming(emitter, sessionId, message, userId, emitterCompleted, heartbeatActive);
            } catch (Exception e) {
                heartbeatActive.set(false);
                log.error("[ChatService] 对话处理异常 - sessionId: {}", sessionId, e);
                sendEvent(emitter, "{\"type\":\"error\",\"content\":\"处理异常，请稍后重试\"}");
                emitter.completeWithError(e);
            } finally {
                SearchContext.clear();
            }
        });

        return emitter;
    }

    /**
     * 真流式处理主流程
     */
    private void handleStreaming(SseEmitter emitter, String sessionId, String message, Long userId,
                                 AtomicBoolean emitterCompleted, AtomicBoolean heartbeatActive) {
        long chainStartMs = System.currentTimeMillis();
        log.info("[ChatService] ===== 知识库问答链路开始 ===== sessionId: {}", sessionId);

        try {
            sendEvent(emitter, "{\"type\":\"status\",\"content\":\"智能体正在思考...\"}");

            // 工具规格
            List<ToolSpecification> toolSpecs = ToolSpecifications.toolSpecificationsFrom(knowledgeTools);

            // ChatMemory（从DB加载历史）
            MessageWindowChatMemory memory = MessageWindowChatMemory.builder()
                    .id(sessionId)
                    .maxMessages(20)
                    .chatMemoryStore(chatMemoryStore)
                    .build();

            // 模型消息列表：System + 历史 + 当前用户消息
            List<ChatMessage> messages = new ArrayList<>();
            messages.add(SystemMessage.from(KnowledgePrompts.SYSTEM_MESSAGE));
            messages.addAll(memory.messages());
            UserMessage userMessage = UserMessage.from(message);
            messages.add(userMessage);
            memory.add(userMessage);

            StringBuilder fullResponse = new StringBuilder();
            AtomicBoolean firstTokenSent = new AtomicBoolean(false);
            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<Throwable> errorRef = new AtomicReference<>();
            AtomicInteger iteration = new AtomicInteger(0);

            executeStreamingLoop(streamingChatModel, messages, toolSpecs, emitter, sessionId, userId,
                    memory, fullResponse, firstTokenSent, latch, errorRef, iteration,
                    emitterCompleted, heartbeatActive, chainStartMs);

            // 阻塞等待流式完成
            long waitTimeout = Math.max(5000, CHAIN_TIMEOUT_MS + 10_000 - (System.currentTimeMillis() - chainStartMs));
            if (!latch.await(waitTimeout, TimeUnit.MILLISECONDS)) {
                heartbeatActive.set(false);
                log.error("[ChatService] 流式超时 - sessionId: {}", sessionId);
                if (!emitterCompleted.get()) {
                    sendEvent(emitter, "{\"type\":\"error\",\"content\":\"智能体响应超时，请稍后重试\"}");
                    emitter.complete();
                }
            }

            if (errorRef.get() != null) {
                heartbeatActive.set(false);
                if (!emitterCompleted.get()) {
                    Throwable error = errorRef.get();
                    String errorMsg = (error.getMessage() != null && error.getMessage().contains("timeout"))
                            ? "智能体响应超时，请检查模型服务后重试" : "智能体响应异常: " + error.getMessage();
                    sendEvent(emitter, "{\"type\":\"error\",\"content\":\"" + escapeJson(errorMsg) + "\"}");
                    emitter.complete();
                }
            }
            log.info("[ChatService] ===== 知识库问答链路结束 ===== sessionId: {}, 总耗时: {} ms",
                    sessionId, System.currentTimeMillis() - chainStartMs);
        } catch (Exception e) {
            heartbeatActive.set(false);
            log.error("[ChatService] 真流式处理异常 - sessionId: {}", sessionId, e);
            sendEvent(emitter, "{\"type\":\"error\",\"content\":\"智能体响应异常\"}");
            emitter.completeWithError(e);
        }
    }

    /**
     * 流式循环：递归调用 streamingModel.chat()，处理工具调用与最终文本生成
     */
    private void executeStreamingLoop(OpenAiStreamingChatModel model, List<ChatMessage> messages,
                                      List<ToolSpecification> toolSpecs, SseEmitter emitter, String sessionId,
                                      Long userId, MessageWindowChatMemory memory,
                                      StringBuilder fullResponse, AtomicBoolean firstTokenSent,
                                      CountDownLatch latch, AtomicReference<Throwable> errorRef,
                                      AtomicInteger iteration, AtomicBoolean emitterCompleted,
                                      AtomicBoolean heartbeatActive, long chainStartMs) {
        if (emitterCompleted.get()) {
            log.warn("[ChatService] SSE已关闭，终止流式循环 - sessionId: {}", sessionId);
            latch.countDown();
            return;
        }
        if (iteration.get() >= MAX_ITERATIONS) {
            log.warn("[ChatService] 达到最大迭代次数({}) - sessionId: {}", MAX_ITERATIONS, sessionId);
            sendEvent(emitter, "{\"type\":\"error\",\"content\":\"工具调用次数过多，请简化请求后重试\"}");
            emitter.complete();
            latch.countDown();
            return;
        }
        if (System.currentTimeMillis() - chainStartMs > CHAIN_TIMEOUT_MS) {
            log.warn("[ChatService] 流式循环超时({}ms) - sessionId: {}", CHAIN_TIMEOUT_MS, sessionId);
            sendEvent(emitter, "{\"type\":\"error\",\"content\":\"智能体响应超时，请稍后重试或简化问题\"}");
            emitter.complete();
            latch.countDown();
            return;
        }

        String thinkingMsg = iteration.get() == 0
                ? "智能体正在分析您的问题..."
                : "正在结合知识库检索结果生成回答...";
        sendEvent(emitter, "{\"type\":\"thinking\",\"content\":\"" + escapeJson(thinkingMsg) + "\"}");

        // 与本服务 dto.ChatRequest 同名，此处使用全限定名引用 langchain4j 的请求模型
        dev.langchain4j.model.chat.request.ChatRequest chatRequest =
                dev.langchain4j.model.chat.request.ChatRequest.builder()
                        .messages(messages)
                        .toolSpecifications(toolSpecs)
                        .build();

        model.chat(chatRequest, new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {
                // 真流式：token 逐个推送
                if (firstTokenSent.compareAndSet(false, true)) {
                    heartbeatActive.set(false);
                    log.info("[ChatService] 首个token到达，停止心跳 - sessionId: {}, 迭代: {}, 耗时: {}ms",
                            sessionId, iteration.get(), System.currentTimeMillis() - chainStartMs);
                }
                fullResponse.append(partialResponse);
                sendEvent(emitter, "{\"type\":\"token\",\"content\":\"" + escapeJson(partialResponse) + "\"}");
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                AiMessage aiMessage = completeResponse.aiMessage();

                if (aiMessage != null && aiMessage.hasToolExecutionRequests()) {
                    log.info("[ChatService] 流式模型请求工具调用 - sessionId: {}, iteration: {}, tools: {}",
                            sessionId, iteration.get(), aiMessage.toolExecutionRequests().size());

                    messages.add(aiMessage);
                    // 过渡文本不持久化，memory 中只保留工具调用请求（INTERNAL 消息被历史接口过滤）
                    if (aiMessage.text() != null && !aiMessage.text().isEmpty()) {
                        memory.add(AiMessage.from(aiMessage.toolExecutionRequests()));
                    } else {
                        memory.add(aiMessage);
                    }

                    for (ToolExecutionRequest request : aiMessage.toolExecutionRequests()) {
                        String toolName = request.name();
                        sendEvent(emitter, "{\"type\":\"tool_call\",\"tool\":\"" + escapeJson(toolName)
                                + "\",\"content\":\"正在检索知识库...\"}");
                        long toolStartMs = System.currentTimeMillis();
                        try {
                            DefaultToolExecutor executor = new DefaultToolExecutor(knowledgeTools, request);
                            String result = executor.execute(request, null);
                            ToolExecutionResultMessage toolResult = ToolExecutionResultMessage.from(request, result);
                            messages.add(toolResult);
                            memory.add(toolResult);
                            log.info("[ChatService] 工具执行完成 - sessionId: {}, tool: {}, resultLength: {}, 耗时: {}ms",
                                    sessionId, toolName, result != null ? result.length() : 0,
                                    System.currentTimeMillis() - toolStartMs);
                            sendEvent(emitter, "{\"type\":\"tool_result\",\"tool\":\"" + escapeJson(toolName)
                                    + "\",\"content\":\"知识库检索完成，正在生成回答...\"}");

                            // 推送检索引用来源（前端展示引用）
                            List<SearchContext.SourceInfo> sources = SearchContext.getAndClear();
                            if (!sources.isEmpty()) {
                                pushSourcesEvent(emitter, sources);
                            }
                        } catch (Exception e) {
                            log.error("[ChatService] 工具执行异常 - sessionId: {}, tool: {}", sessionId, toolName, e);
                            ToolExecutionResultMessage errorResult =
                                    ToolExecutionResultMessage.from(request, "工具执行异常: " + e.getMessage());
                            messages.add(errorResult);
                            memory.add(errorResult);
                            sendEvent(emitter, "{\"type\":\"tool_result\",\"tool\":\"" + escapeJson(toolName)
                                    + "\",\"content\":\"知识库检索异常，正在处理...\"}");
                        }
                    }

                    // 工具调用阶段的文本不作为最终回复
                    fullResponse.setLength(0);
                    firstTokenSent.set(false);
                    // 下一轮LLM思考期间恢复心跳
                    heartbeatActive.set(true);

                    iteration.incrementAndGet();
                    executeStreamingLoop(model, messages, toolSpecs, emitter, sessionId, userId,
                            memory, fullResponse, firstTokenSent, latch, errorRef, iteration,
                            emitterCompleted, heartbeatActive, chainStartMs);
                } else {
                    // ========== 最终响应（无工具调用） ==========
                    long elapsed = System.currentTimeMillis() - chainStartMs;
                    log.info("[ChatService] 流式响应完成 - sessionId: {}, 总耗时: {}ms, length: {}",
                            sessionId, elapsed, fullResponse.length());

                    if (emitterCompleted.get()) {
                        log.warn("[ChatService] SSE已关闭，跳过收尾 - sessionId: {}", sessionId);
                        latch.countDown();
                        return;
                    }

                    String aiResponse = fullResponse.toString();
                    if ((aiResponse == null || aiResponse.isEmpty()) && aiMessage != null) {
                        aiResponse = aiMessage.text();
                    }
                    if (aiResponse == null || aiResponse.isEmpty()) {
                        sendEvent(emitter, "{\"type\":\"error\",\"content\":\"智能体返回为空\"}");
                        emitter.complete();
                        latch.countDown();
                        return;
                    }

                    // 保存最终AI回复到 memory（持久化）
                    try {
                        memory.add(AiMessage.from(aiResponse));
                    } catch (Exception e) {
                        log.warn("[ChatService] 保存最终AI回复到memory失败 - sessionId: {}", sessionId, e);
                    }

                    // 输出脱敏
                    aiResponse = outputSanitizer.sanitize(aiResponse, sessionId);

                    sessionService.touch(sessionId);
                    sendEvent(emitter, "{\"type\":\"complete\",\"content\":\"" + escapeJson(aiResponse) + "\"}");
                    emitter.complete();
                    latch.countDown();
                }
            }

            @Override
            public void onError(Throwable error) {
                heartbeatActive.set(false);
                log.error("[ChatService] 流式响应异常 - sessionId: {}", sessionId, error);
                errorRef.set(error);
                latch.countDown();
            }
        });
    }

    private void pushSourcesEvent(SseEmitter emitter, List<SearchContext.SourceInfo> sources) {
        try {
            List<Map<String, Object>> list = sources.stream()
                    .map(s -> {
                        Map<String, Object> m = new java.util.LinkedHashMap<>();
                        m.put("fileName", s.fileName());
                        m.put("category", s.category());
                        m.put("score", Math.round(s.score() * 1000) / 1000.0);
                        return m;
                    })
                    .toList();
            Map<String, Object> event = new java.util.LinkedHashMap<>();
            event.put("type", "sources");
            event.put("sources", list);
            sendEvent(emitter, OBJECT_MAPPER.writeValueAsString(event));
        } catch (Exception e) {
            log.warn("[ChatService] sources事件序列化失败: {}", e.getMessage());
        }
    }

    /**
     * 解析或创建 sessionId（单用户，无租户校验）
     */
    private String resolveSessionId(String requestSessionId, Long userId) {
        if (requestSessionId != null && !requestSessionId.isEmpty()) {
            if (sessionService.getValidSession(requestSessionId) != null) {
                return requestSessionId;
            }
            log.warn("[ChatService] 会话不存在或已删除，创建新会话 - 请求sessionId: {}", requestSessionId);
        }
        return sessionService.createSession(userId, null).getSessionId();
    }

    /**
     * 心跳保活：LLM 思考期间每 10 秒推送 heartbeat，防止代理超时断连
     */
    private ScheduledExecutorService startHeartbeat(SseEmitter emitter, String sessionId,
                                                    AtomicBoolean emitterCompleted) {
        ScheduledExecutorService heartbeatScheduler = Executors.newSingleThreadScheduledExecutor();
        AtomicBoolean heartbeatActive = new AtomicBoolean(true);
        heartbeatScheduler.scheduleAtFixedRate(() -> {
            if (heartbeatActive.get() && !emitterCompleted.get()) {
                try {
                    synchronized (emitter) {
                        if (!emitterCompleted.get()) {
                            emitter.send(SseEmitter.event().name("heartbeat")
                                    .data("{\"type\":\"heartbeat\",\"timestamp\":\"" + System.currentTimeMillis() + "\"}"));
                        }
                    }
                } catch (Exception e) {
                    heartbeatActive.set(false);
                    heartbeatScheduler.shutdown();
                    log.warn("[SSE] 心跳发送失败，连接可能已断开 - sessionId: {}, error: {}", sessionId, e.getMessage());
                }
            } else if (emitterCompleted.get()) {
                heartbeatScheduler.shutdown();
            }
        }, 5, 10, TimeUnit.SECONDS);

        emitter.onCompletion(() -> {
            emitterCompleted.set(true);
            heartbeatActive.set(false);
            heartbeatScheduler.shutdownNow();
            log.info("[SSE] 完成 - sessionId: {}", sessionId);
        });
        emitter.onTimeout(() -> {
            emitterCompleted.set(true);
            heartbeatActive.set(false);
            heartbeatScheduler.shutdownNow();
            log.warn("[SSE] 超时 - sessionId: {}", sessionId);
            try {
                emitter.send(SseEmitter.event().data("{\"type\":\"error\",\"content\":\"响应超时，请稍后重试\"}"));
            } catch (Exception ignored) {
            }
            emitter.complete();
        });
        emitter.onError(e -> {
            emitterCompleted.set(true);
            heartbeatActive.set(false);
            heartbeatScheduler.shutdownNow();
            log.error("[SSE] 异常 - sessionId: {}", sessionId, e);
        });
        return heartbeatScheduler;
    }

    private SseEmitter blockedEmitter(String message) {
        SseEmitter blockedEmitter = new SseEmitter(10_000L);
        try {
            blockedEmitter.send(SseEmitter.event()
                    .data("{\"type\":\"error\",\"content\":\"" + escapeJson(message) + "\"}"));
            blockedEmitter.send(SseEmitter.event().data("{\"type\":\"complete\"}"));
            blockedEmitter.complete();
        } catch (Exception e) {
            log.debug("[ChatService] 拦截响应写入失败: {}", e.getMessage());
        }
        return blockedEmitter;
    }

    private void sendEvent(SseEmitter emitter, String data) {
        try {
            synchronized (emitter) {
                emitter.send(SseEmitter.event().data(data));
            }
        } catch (Exception e) {
            log.warn("[SSE] 发送事件失败（可能已关闭）: {}, data前50字: {}", e.getMessage(),
                    data != null && data.length() > 50 ? data.substring(0, 50) + "..." : data);
        }
    }

    private String escapeJson(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
