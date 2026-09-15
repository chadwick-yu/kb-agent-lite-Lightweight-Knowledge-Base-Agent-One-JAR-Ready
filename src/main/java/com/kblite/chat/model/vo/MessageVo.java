package com.kblite.chat.model.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.kblite.chat.entity.AgentMessage;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 历史消息 VO
 *
 * @author kb-agent-lite
 */
@Data
public class MessageVo {

    private Long id;
    private String role;
    private String content;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;

    public static MessageVo from(AgentMessage msg) {
        MessageVo vo = new MessageVo();
        vo.setId(msg.getId());
        vo.setRole(msg.getRole());
        vo.setContent(msg.getContent());
        vo.setCreateTime(msg.getCreateTime());
        return vo;
    }
}
