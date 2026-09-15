package com.kblite.chat.memory;

import com.kblite.chat.entity.AgentMessage;
import com.kblite.chat.mapper.AgentMessageMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * LangChain4j ChatMemoryStore 持久化实现（H2 存储，滑动窗口）
 * 消息全量 delete+reinsert 写入 agent_message 表，滑动窗口控制上下文长度
 *
 * @author kb-agent-lite
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PersistentChatMemoryStore implements ChatMemoryStore {

    /** 单会话上下文 Token 上限（超出删除最旧消息） */
    private static final int MAX_CONTEXT_TOKENS = 8000;
    private static final int MAX_RETRY = 2;

    /** 单用户部署，固定用户ID */
    public static final long FIXED_USER_ID = 1L;

    private final AgentMessageMapper agentMessageMapper;
    private final PlatformTransactionManager transactionManager;

    private volatile TransactionTemplate transactionTemplate;

    private TransactionTemplate getTransactionTemplate() {
        if (transactionTemplate == null) {
            synchronized (this) {
                if (transactionTemplate == null) {
                    transactionTemplate = new TransactionTemplate(transactionManager);
                }
            }
        }
        return transactionTemplate;
    }

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        String sessionId = memoryId.toString();
        return executeWithRetry(() -> {
            List<AgentMessage> dbMessages = agentMessageMapper.selectBySessionId(sessionId, 200);
            List<ChatMessage> result = dbMessages.stream()
                    .map(this::convertToChatMessage)
                    .filter(msg -> msg != null)
                    .collect(Collectors.toList());
            log.debug("[ChatMemoryStore] 加载会话消息 - sessionId: {}, count: {}", sessionId, result.size());
            return result;
        }, "getMessages-" + sessionId);
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        String sessionId = memoryId.toString();
        executeWithRetry(() -> {
            // 编程式事务：delete+reinsert 原子化，避免半删半插
            getTransactionTemplate().execute(status -> {
                agentMessageMapper.deleteBySessionId(sessionId);
                int totalTokens = 0;
                for (ChatMessage msg : messages) {
                    AgentMessage entity = convertToEntity(sessionId, msg);
                    if (entity != null) {
                        agentMessageMapper.insert(entity);
                        totalTokens += entity.getTokenCount();
                    }
                }
                if (totalTokens > MAX_CONTEXT_TOKENS) {
                    trimOldMessages(sessionId, totalTokens);
                }
                log.debug("[ChatMemoryStore] 更新会话消息 - sessionId: {}, count: {}, tokens: {}",
                        sessionId, messages.size(), totalTokens);
                return null;
            });
            return null;
        }, "updateMessages-" + sessionId);
    }

    @Override
    public void deleteMessages(Object memoryId) {
        String sessionId = memoryId.toString();
        executeWithRetry(() -> {
            agentMessageMapper.deleteBySessionId(sessionId);
            log.info("[ChatMemoryStore] 删除会话消息 - sessionId: {}", sessionId);
            return null;
        }, "deleteMessages-" + sessionId);
    }

    private <T> T executeWithRetry(RetryableAction<T> action, String opName) {
        for (int attempt = 1; attempt <= MAX_RETRY; attempt++) {
            try {
                return action.execute();
            } catch (Exception e) {
                if (attempt == MAX_RETRY) {
                    log.error("[ChatMemoryStore] {} 重试耗尽 - error: {}", opName, e.getMessage());
                    throw e;
                }
                log.warn("[ChatMemoryStore] {} 第{}次执行失败，{}ms后重试 - error: {}",
                        opName, attempt, 500 * attempt, e.getMessage());
                try {
                    Thread.sleep(500L * attempt);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        throw new IllegalStateException("不应到达此处");
    }

    @FunctionalInterface
    private interface RetryableAction<T> {
        T execute();
    }

    private void trimOldMessages(String sessionId, int currentTokens) {
        if (currentTokens <= MAX_CONTEXT_TOKENS) {
            return;
        }
        List<AgentMessage> all = agentMessageMapper.selectBySessionId(sessionId, null);
        int tokens = currentTokens;
        for (AgentMessage msg : all) {
            if (tokens <= MAX_CONTEXT_TOKENS * 0.8) {
                break;
            }
            agentMessageMapper.deleteById(msg.getId());
            tokens -= msg.getTokenCount();
        }
    }

    private ChatMessage convertToChatMessage(AgentMessage entity) {
        if (entity == null || entity.getRole() == null || entity.getContent() == null) {
            return null;
        }
        try {
            switch (entity.getRole()) {
                case "USER":
                    return UserMessage.from(entity.getContent());
                case "AI":
                    // 空内容AI消息跳过恢复，避免污染历史展示与会话标题
                    if (entity.getContent().isEmpty()) {
                        return null;
                    }
                    return AiMessage.from(entity.getContent());
                case "SYSTEM":
                    return SystemMessage.from(entity.getContent());
                case "TOOL":
                    // 工具结果消息不参与下一轮上下文恢复（保持消息链合法：用户+AI）
                    return null;
                default:
                    log.warn("[ChatMemoryStore] 未知消息类型兜底 - role: {}", entity.getRole());
                    return UserMessage.from(entity.getContent());
            }
        } catch (Exception e) {
            log.warn("[ChatMemoryStore] 消息反序列化失败 - role: {}, error: {}", entity.getRole(), e.getMessage());
            return null;
        }
    }

    private AgentMessage convertToEntity(String sessionId, ChatMessage msg) {
        AgentMessage entity = new AgentMessage();
        entity.setSessionId(sessionId);
        entity.setUserId(FIXED_USER_ID);
        entity.setRole(mapRole(msg.type().name()));
        entity.setContent(textFrom(msg));
        // 含工具执行请求的AI消息为中间调度消息，标记 INTERNAL 供历史接口过滤
        if (msg instanceof AiMessage aiMsg && aiMsg.toolExecutionRequests() != null
                && !aiMsg.toolExecutionRequests().isEmpty()) {
            entity.setContentType("INTERNAL");
        } else {
            entity.setContentType("TEXT");
        }
        entity.setTokenCount(estimateTokens(entity.getContent()));
        entity.setIsDeleted(0);
        return entity;
    }

    private String mapRole(String typeName) {
        if (typeName == null) {
            return "UNKNOWN";
        }
        return switch (typeName) {
            case "USER" -> "USER";
            case "AI" -> "AI";
            case "SYSTEM" -> "SYSTEM";
            case "TOOL_EXECUTION_REQUEST", "TOOL_EXECUTION_RESULT" -> "TOOL";
            default -> typeName.length() > 16 ? typeName.substring(0, 16) : typeName;
        };
    }

    private String textFrom(ChatMessage msg) {
        if (msg instanceof UserMessage userMessage) {
            return userMessage.singleText();
        } else if (msg instanceof AiMessage aiMessage) {
            return aiMessage.text() != null ? aiMessage.text() : "";
        } else if (msg instanceof SystemMessage systemMessage) {
            return systemMessage.text();
        } else if (msg instanceof ToolExecutionResultMessage toolResult) {
            // 存纯净文本，避免 toString 包装污染数据
            return toolResult.text() != null ? toolResult.text() : "";
        }
        return msg.toString();
    }

    /** 简化 Token 估算：中文每字≈2 token，英文词≈1.3 token，其他字符≈0.25 token */
    private int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int chinese = 0, words = 0, otherChars = 0;
        boolean inWord = false;
        for (char c : text.toCharArray()) {
            if (c >= '一' && c <= '\u9fff') {
                chinese++;
                inWord = false;
            } else if (Character.isLetterOrDigit(c)) {
                otherChars++;
                if (!inWord) {
                    words++;
                    inWord = true;
                }
            } else {
                inWord = false;
            }
        }
        return (int) (chinese * 2.0 + words * 1.3 + otherChars * 0.25);
    }
}
