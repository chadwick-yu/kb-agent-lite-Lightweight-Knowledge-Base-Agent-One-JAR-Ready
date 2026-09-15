package com.kblite.common;

/**
 * 业务异常（消息可直接展示给用户）
 *
 * @author kb-agent-lite
 */
public class BizException extends RuntimeException {

    public BizException(String message) {
        super(message);
    }

    public BizException(String message, Throwable cause) {
        super(message, cause);
    }
}
