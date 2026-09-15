package com.kblite.chat.controller;

import com.kblite.chat.dto.ChatRequest;
import com.kblite.chat.entity.AgentMessage;
import com.kblite.chat.mapper.AgentMessageMapper;
import com.kblite.chat.memory.PersistentChatMemoryStore;
import com.kblite.chat.service.ChatService;
import com.kblite.chat.service.SessionService;
import com.kblite.common.ApiResult;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识库问答对话控制器（SSE 流式）
 *
 * @author kb-agent-lite
 */
@Slf4j
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;
    private final SessionService sessionService;
    private final AgentMessageMapper agentMessageMapper;

    public ChatController(ChatService chatService, SessionService sessionService,
                          AgentMessageMapper agentMessageMapper) {
        this.chatService = chatService;
        this.sessionService = sessionService;
        this.agentMessageMapper = agentMessageMapper;
    }

    /**
     * 对话入口（SSE 流式）
     * 事件：session/status/thinking/token/tool_call/tool_result/sources/complete/error/heartbeat
     */
    @PostMapping(produces = "text/event-stream;charset=UTF-8")
    public SseEmitter chat(@Valid @RequestBody ChatRequest request, HttpServletResponse response) {
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader("Cache-Control", "no-cache, no-transform");
        response.setHeader("Connection", "keep-alive");
        Long userId = PersistentChatMemoryStore.FIXED_USER_ID;
        log.info("[Chat] 收到对话请求 - userId: {}, sessionId: {}, message长度: {}",
                userId, request.getSessionId(), request.getMessage().length());
        return chatService.chatStream(request, userId);
    }

    /**
     * 断线恢复：返回会话最近一条 AI 回复的完整内容
     */
    @GetMapping("/recover/{sessionId}")
    public ApiResult<Map<String, Object>> recover(@PathVariable("sessionId") String sessionId) {
        if (sessionService.getValidSession(sessionId) == null) {
            return ApiResult.fail(404, "会话不存在或已删除");
        }
        List<AgentMessage> messages = agentMessageMapper.selectBySessionId(sessionId, 10);
        for (int i = messages.size() - 1; i >= 0; i--) {
            AgentMessage msg = messages.get(i);
            if ("AI".equals(msg.getRole())
                    && msg.getContent() != null && !msg.getContent().isEmpty()) {
                Map<String, Object> result = new HashMap<>();
                result.put("sessionId", sessionId);
                result.put("content", msg.getContent());
                result.put("contentType", msg.getContentType());
                result.put("createTime", msg.getCreateTime());
                return ApiResult.ok(result);
            }
        }
        return ApiResult.fail(404, "该会话暂无AI回复，可能仍在处理中，请稍后重试");
    }

    /**
     * 健康检查（免鉴权）
     */
    @GetMapping("/health")
    public ApiResult<String> health() {
        return ApiResult.ok("kb-agent-lite is running");
    }
}
