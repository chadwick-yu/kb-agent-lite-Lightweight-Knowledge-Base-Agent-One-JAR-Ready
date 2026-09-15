package com.kblite.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 知识库文档实体（对应表 knowledge_document）
 *
 * @author kb-agent-lite
 */
@Data
@TableName("knowledge_document")
public class KnowledgeDocument {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 文档唯一标识（UUID） */
    private String documentId;

    /** 文档标题 */
    private String title;

    /** 原始文件名 */
    private String originalFileName;

    /** 文件 MIME 类型 */
    private String fileType;

    /** 文件大小（字节） */
    private Long fileSize;

    /** 本地磁盘相对路径（category/yyyy/MM/uuid.ext） */
    private String filePath;

    /** 分类ID */
    private String categoryId;

    /** 分类名称（冗余，用于向量元数据） */
    private String categoryName;

    /** 文件扩展名 */
    private String fileExtension;

    /** 解析后文本长度（字符数） */
    private Integer contentLength;

    /** 分块数量 */
    private Integer chunkCount;

    /** 向量存储数量 */
    private Integer vectorCount;

    /** 使用的 Embedding 模型 */
    private String embeddingModel;

    /** 向量维度 */
    private Integer vectorDimension;

    /** 向量化状态：0-未开始 1-处理中 2-成功 3-失败 */
    private Integer vectorStatus;

    /** 向量化失败原因 */
    private String vectorError;

    /** 文档元数据（JSON） */
    private String metadataJson;

    /** 文档状态：0-已删除 1-正常 */
    private Integer status;

    /** 备注 */
    private String remark;

    /** 标签（逗号分隔） */
    private String tags;

    private String createUser;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;

    private String updateUser;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updateTime;

    /** 上传人 */
    private String userId;
}
