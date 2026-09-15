package com.kblite.chat.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.kblite.chat.entity.AgentSession;
import com.kblite.chat.mapper.AgentSessionMapper;
import com.kblite.chat.memory.PersistentChatMemoryStore;
import com.kblite.chat.mapper.AgentMessageMapper;
import com.kblite.common.BizException;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 会话管理服务
 *
 * @author kb-agent-lite
 */
@Slf4j
@Service
public class SessionService {

    @Resource
    private AgentSessionMapper agentSessionMapper;
    @Resource
    private AgentMessageMapper agentMessageMapper;
    @Resource
    private PersistentChatMemoryStore chatMemoryStore;

    /**
     * 创建新会话
     */
    @Transactional(rollbackFor = Exception.class)
    public AgentSession createSession(Long userId, String title) {
        AgentSession session = new AgentSession();
        session.setSessionId(UUID.randomUUID().toString().replace("-", ""));
        session.setUserId(userId);
        session.setTitle(title == null || title.trim().isEmpty() ? "新会话" : title.trim());
        session.setTitleSource(title == null || title.trim().isEmpty() ? 0 : 1);
        session.setStatus(1);
        session.setTokenCount(0);
        session.setModelType("");
        session.setIsDeleted(0);
        LocalDateTime now = LocalDateTime.now();
        session.setCreateTime(now);
        session.setUpdateTime(now);
        agentSessionMapper.insert(session);
        log.info("[Session] 创建会话 - sessionId: {}, userId: {}", session.getSessionId(), userId);
        return session;
    }

    /**
     * 会话列表（按活跃时间倒序，标题按码点截断防超长）
     */
    public List<AgentSession> listSessions(Long userId, Integer status) {
        List<AgentSession> sessions = agentSessionMapper.selectList(
                Wrappers.<AgentSession>lambdaQuery()
                        .eq(AgentSession::getUserId, userId)
                        .eq(AgentSession::getIsDeleted, 0)
                        .eq(status != null, AgentSession::getStatus, status)
                        .orderByDesc(AgentSession::getUpdateTime));
        for (AgentSession session : sessions) {
            session.setTitle(truncateTitle(session.getTitle()));
        }
        return sessions;
    }

    private String truncateTitle(String title) {
        if (title == null) {
            return null;
        }
        int[] codePoints = title.codePoints().limit(51).toArray();
        if (codePoints.length <= 50) {
            return title;
        }
        return new String(codePoints, 0, 50) + "...";
    }

    /**
     * 删除会话（逻辑删除 + 清理消息与 Memory）
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteSession(String sessionId, Long userId) {
        int updated = agentSessionMapper.update(null, Wrappers.<AgentSession>lambdaUpdate()
                .eq(AgentSession::getSessionId, sessionId)
                .eq(AgentSession::getUserId, userId)
                .eq(AgentSession::getIsDeleted, 0)
                .set(AgentSession::getIsDeleted, 1)
                .set(AgentSession::getStatus, 2)
                .set(AgentSession::getUpdateTime, LocalDateTime.now()));
        if (updated <= 0) {
            return false;
        }
        // 级联清理消息与 Memory
        agentMessageMapper.deleteBySessionId(sessionId);
        chatMemoryStore.deleteMessages(sessionId);
        log.info("[Session] 删除会话 - sessionId: {}, userId: {}", sessionId, userId);
        return true;
    }

    /**
     * 更新会话标题（用户自定义，title_source=1 后不再被动态标题覆盖）
     */
    public boolean updateTitle(String sessionId, Long userId, String title) {
        return agentSessionMapper.update(null, Wrappers.<AgentSession>lambdaUpdate()
                .eq(AgentSession::getSessionId, sessionId)
                .eq(AgentSession::getUserId, userId)
                .eq(AgentSession::getIsDeleted, 0)
                .set(AgentSession::getTitle, title)
                .set(AgentSession::getTitleSource, 1)
                .set(AgentSession::getUpdateTime, LocalDateTime.now())) > 0;
    }

    /**
     * 动态标题（title_source=0 时取最后一句用户消息）并刷新活跃时间
     */
    public void touchWithDynamicTitle(String sessionId, String userMessage) {
        AgentSession session = getValidSession(sessionId);
        if (session == null) {
            return;
        }
        AgentSession update = new AgentSession();
        update.setId(session.getId());
        update.setUpdateTime(LocalDateTime.now());
        if (session.getTitleSource() == null || session.getTitleSource() == 0) {
            update.setTitle(truncateTitle(userMessage));
        }
        agentSessionMapper.updateById(update);
    }

    /** 仅刷新活跃时间（列表按最近活跃排序） */
    public void touch(String sessionId) {
        AgentSession session = getValidSession(sessionId);
        if (session == null) {
            return;
        }
        AgentSession update = new AgentSession();
        update.setId(session.getId());
        update.setUpdateTime(LocalDateTime.now());
        agentSessionMapper.updateById(update);
    }

    /**
     * 查询有效会话
     */
    public AgentSession getValidSession(String sessionId) {
        return agentSessionMapper.selectBySessionId(sessionId);
    }

    /**
     * 校验会话归属（单用户部署仅校验存在性，保留接口形态）
     */
    public AgentSession getValidSessionOrThrow(String sessionId) {
        AgentSession session = getValidSession(sessionId);
        if (session == null) {
            throw new BizException("会话不存在或已删除");
        }
        return session;
    }
}
