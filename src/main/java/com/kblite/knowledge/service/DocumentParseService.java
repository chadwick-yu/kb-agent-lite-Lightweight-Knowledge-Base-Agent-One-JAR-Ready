package com.kblite.knowledge.service;

import com.kblite.knowledge.entity.KnowledgeDocument;
import com.kblite.knowledge.model.DocumentParseResult;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 文档解析服务接口
 *
 * @author kb-agent-lite
 */
public interface DocumentParseService {

    /**
     * 解析文档并标准化文本
     */
    DocumentParseResult parseAndNormalize(MultipartFile file);

    /**
     * 对已有文档（原始文件已存本地磁盘）进行解析和向量化
     *
     * @param document 文档实体（必须包含 filePath、documentId）
     * @return 向量化存储的向量数量
     */
    int vectorizeDocument(KnowledgeDocument document);

    /**
     * 搜索相似文档
     */
    List<VectorStoreService.SimilarDocument> searchSimilarDocuments(String query, int maxResults);

    /**
     * 校验文件格式是否支持
     */
    void validateFileType(MultipartFile file);

    /**
     * 校验文件大小是否超限
     */
    void validateFileSize(MultipartFile file);

    /**
     * 解析文件真实 MIME 类型（浏览器上报 octet-stream/空时用 Tika 嗅探）
     */
    String resolveFileType(MultipartFile file);
}
