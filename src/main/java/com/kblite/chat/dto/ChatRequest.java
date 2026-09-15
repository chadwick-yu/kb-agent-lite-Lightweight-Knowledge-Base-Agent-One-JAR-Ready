package com.kblite.chat.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 对话请求体
 *
 * @author kb-agent-lite
 */
@Data
public class ChatRequest {

    /** 会话ID（可空，为空自动创建新会话，响应 session 事件返回） */
    private String sessionId;

    @NotBlank(message = "消息内容不能为空")
    private String message;
}
