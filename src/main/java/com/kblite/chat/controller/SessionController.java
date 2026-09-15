package com.kblite.chat.controller;

import com.kblite.chat.entity.AgentMessage;
import com.kblite.chat.entity.AgentSession;
import com.kblite.chat.memory.PersistentChatMemoryStore;
import com.kblite.chat.mapper.AgentMessageMapper;
import com.kblite.chat.model.vo.MessageVo;
import com.kblite.chat.service.SessionService;
import com.kblite.common.ApiResult;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 会话管理控制器
 *
 * @author kb-agent-lite
 */
@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    private final SessionService sessionService;
    private final AgentMessageMapper agentMessageMapper;

    public SessionController(SessionService sessionService, AgentMessageMapper agentMessageMapper) {
        this.sessionService = sessionService;
        this.agentMessageMapper = agentMessageMapper;
    }

    /** 会话列表 */
    @GetMapping
    public ApiResult<List<AgentSession>> list(
            @RequestParam(value = "status", required = false) Integer status) {
        Long userId = PersistentChatMemoryStore.FIXED_USER_ID;
        return ApiResult.ok(sessionService.listSessions(userId, status));
    }

    /** 新建会话，body: {"title": "..."}（可省略） */
    @PostMapping
    public ApiResult<AgentSession> create(@RequestBody(required = false) Map<String, String> body) {
        String title = body == null ? null : body.get("title");
        Long userId = PersistentChatMemoryStore.FIXED_USER_ID;
        return ApiResult.ok(sessionService.createSession(userId, title));
    }

    /** 删除会话（级联清理消息与记忆） */
    @DeleteMapping("/{sessionId}")
    public ApiResult<Boolean> delete(@PathVariable("sessionId") String sessionId) {
        Long userId = PersistentChatMemoryStore.FIXED_USER_ID;
        boolean success = sessionService.deleteSession(sessionId, userId);
        return success ? ApiResult.ok(true) : ApiResult.fail(404, "会话不存在或无权删除");
    }

    /** 更新会话标题，body: {"title": "..."} */
    @PutMapping("/{sessionId}/title")
    public ApiResult<Boolean> updateTitle(@PathVariable("sessionId") String sessionId,
                                          @RequestBody Map<String, String> body) {
        String title = body == null ? null : body.get("title");
        if (title == null || title.trim().isEmpty()) {
            return ApiResult.fail(400, "标题不能为空");
        }
        Long userId = PersistentChatMemoryStore.FIXED_USER_ID;
        boolean success = sessionService.updateTitle(sessionId, userId, title.trim());
        return success ? ApiResult.ok(true) : ApiResult.fail(404, "会话不存在");
    }

    /** 会话历史消息（过滤系统/工具/中间消息，供前端渲染） */
    @GetMapping("/{sessionId}/messages")
    public ApiResult<List<MessageVo>> listMessages(@PathVariable("sessionId") String sessionId) {
        Long userId = PersistentChatMemoryStore.FIXED_USER_ID;
        AgentSession session = sessionService.getValidSession(sessionId);
        if (session == null) {
            return ApiResult.fail(404, "会话不存在");
        }
        if (!userId.equals(session.getUserId())) {
            return ApiResult.fail(403, "无权访问该会话");
        }
        List<MessageVo> voList = agentMessageMapper.selectBySessionId(sessionId, null).stream()
                .filter(msg -> !"SYSTEM".equals(msg.getRole()))
                .filter(msg -> !"TOOL".equals(msg.getRole()))
                .filter(msg -> !"INTERNAL".equals(msg.getContentType()))
                .filter(msg -> !("AI".equals(msg.getRole())
                        && (msg.getContent() == null || msg.getContent().isEmpty())))
                .filter(msg -> msg.getContent() == null
                        || (!msg.getContent().startsWith("ToolExecutionResultMessage")
                        && !msg.getContent().startsWith("ToolExecutionRequestMessage")))
                .map(MessageVo::from)
                .collect(Collectors.toList());
        return ApiResult.ok(voList);
    }
}
