package com.kblite.knowledge.exception;

/**
 * 不支持的文件类型异常
 *
 * @author kb-agent-lite
 */
public class UnsupportedFileTypeException extends RuntimeException {

    public UnsupportedFileTypeException(String message) {
        super(message);
    }
}
