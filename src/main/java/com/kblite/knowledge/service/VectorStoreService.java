package com.kblite.knowledge.service;

import java.util.List;
import java.util.Map;

/**
 * 向量存储服务接口
 * 当前实现为内嵌向量库（InMemoryEmbeddingStore + 本地文件持久化），
 * 文档量增长可平滑替换为 Qdrant 等外部向量库，只需新增实现类
 *
 * @author kb-agent-lite
 */
public interface VectorStoreService {

    /**
     * 将文档内容分块并向量化存储
     *
     * @param documentId 文档ID
     * @param content    文档内容
     * @param metadata   元数据
     * @return 存储的向量数量
     */
    int storeDocument(String documentId, String content, Map<String, String> metadata);

    /**
     * 搜索相似文档
     *
     * @param query      查询文本
     * @param maxResults 最大结果数
     * @return 相似文档列表（包含分数和内容）
     */
    List<SimilarDocument> searchSimilarDocuments(String query, int maxResults);

    /**
     * 删除文档的所有向量块
     *
     * @param documentId 文档ID
     * @return 是否删除成功
     */
    boolean deleteDocument(String documentId);

    /**
     * 相似文档结果
     */
    class SimilarDocument {
        private final String documentId;
        private final String content;
        private final double score;
        private final Map<String, Object> metadata;

        public SimilarDocument(String documentId, String content, double score, Map<String, Object> metadata) {
            this.documentId = documentId;
            this.content = content;
            this.score = score;
            this.metadata = metadata;
        }

        public String getDocumentId() {
            return documentId;
        }

        public String getContent() {
            return content;
        }

        public double getScore() {
            return score;
        }

        public Map<String, Object> getMetadata() {
            return metadata;
        }
    }
}
