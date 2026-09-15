package com.kblite.chat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 智能体会话实体（对应表 agent_session）
 *
 * @author kb-agent-lite
 */
@Data
@TableName("agent_session")
public class AgentSession {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 会话唯一标识（UUID） */
    private String sessionId;

    private Long userId;

    /** 会话标题（最后一句用户消息或用户自定义） */
    private String title;

    /** 标题来源：0-动态（最后一句用户消息） 1-用户自定义 */
    private Integer titleSource;

    /** 状态：1-正常 0-已归档 2-已删除 */
    private Integer status;

    private Integer tokenCount;

    private String modelType;

    /** 逻辑删除：0-正常 1-删除 */
    private Integer isDeleted;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
