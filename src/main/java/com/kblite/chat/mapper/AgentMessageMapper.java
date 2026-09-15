package com.kblite.chat.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.kblite.chat.entity.AgentMessage;

import java.util.List;

/**
 * 智能体消息历史 Mapper（ChatMemoryStore 持久化对接）
 * 使用 default 方法 + MP 条件构造，免去 XML
 *
 * @author kb-agent-lite
 */
public interface AgentMessageMapper extends BaseMapper<AgentMessage> {

    /**
     * 按会话ID查询有效消息列表（用于恢复 ChatMemory），limit 为 null 时取全部
     */
    default List<AgentMessage> selectBySessionId(String sessionId, Integer limit) {
        LambdaQueryWrapper<AgentMessage> wrapper = Wrappers.lambdaQuery(AgentMessage.class)
                .eq(AgentMessage::getSessionId, sessionId)
                .eq(AgentMessage::getIsDeleted, 0)
                .orderByAsc(AgentMessage::getId);
        if (limit != null && limit > 0) {
            wrapper.last("LIMIT " + limit);
        }
        return selectList(wrapper);
    }

    /**
     * 物理删除会话全部消息
     */
    default int deleteBySessionId(String sessionId) {
        return delete(Wrappers.lambdaQuery(AgentMessage.class)
                .eq(AgentMessage::getSessionId, sessionId));
    }
}
