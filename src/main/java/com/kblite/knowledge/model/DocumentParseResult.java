package com.kblite.knowledge.model;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 文档解析结果
 *
 * @author kb-agent-lite
 */
@Data
@Builder
public class DocumentParseResult {

    /** 文档唯一标识 */
    private String documentId;

    /** 原始文件名 */
    private String originalFileName;

    /** 文件类型（MIME） */
    private String fileType;

    /** 文件大小（字节） */
    private long fileSize;

    /** 解析后的纯文本内容 */
    private String content;

    /** 元数据（作者、创建时间等） */
    private Map<String, String> metadata;

    private LocalDateTime parseTime;

    private boolean success;

    /** 错误信息（失败时） */
    private String errorMessage;

    /** 向量存储数量 */
    private Integer vectorCount;
}
