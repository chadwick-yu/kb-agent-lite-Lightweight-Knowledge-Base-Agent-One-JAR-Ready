package com.kblite.knowledge.exception;

/**
 * 文档不存在异常
 *
 * @author kb-agent-lite
 */
public class DocumentNotFoundException extends RuntimeException {

    public DocumentNotFoundException(String message) {
        super(message);
    }
}
