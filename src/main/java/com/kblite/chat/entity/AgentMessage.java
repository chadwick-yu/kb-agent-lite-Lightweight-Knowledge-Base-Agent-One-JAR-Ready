package com.kblite.chat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 智能体消息历史实体（对应表 agent_message，ChatMemoryStore 持久化载体）
 *
 * @author kb-agent-lite
 */
@Data
@TableName("agent_message")
public class AgentMessage {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 所属会话ID */
    private String sessionId;

    private Long userId;

    /** 角色：USER / AI / SYSTEM / TOOL */
    private String role;

    private String content;

    /** 内容类型：TEXT-文本 INTERNAL-工具调用中间消息 */
    private String contentType;

    private Integer tokenCount;

    /** 扩展元数据JSON（如引用来源） */
    private String metadataJson;

    private Integer isDeleted;

    private LocalDateTime createTime;
}
