package com.kblite.knowledge.exception;

/**
 * 文档解析异常
 *
 * @author kb-agent-lite
 */
public class DocumentParseException extends RuntimeException {

    public DocumentParseException(String message) {
        super(message);
    }

    public DocumentParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
