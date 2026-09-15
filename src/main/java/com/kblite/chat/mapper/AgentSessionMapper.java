package com.kblite.chat.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.kblite.chat.entity.AgentSession;

/**
 * 智能体会话 Mapper
 *
 * @author kb-agent-lite
 */
public interface AgentSessionMapper extends BaseMapper<AgentSession> {

    /**
     * 按 sessionId 查询有效会话
     */
    default AgentSession selectBySessionId(String sessionId) {
        return selectOne(Wrappers.<AgentSession>lambdaQuery()
                .eq(AgentSession::getSessionId, sessionId)
                .eq(AgentSession::getIsDeleted, 0));
    }
}
