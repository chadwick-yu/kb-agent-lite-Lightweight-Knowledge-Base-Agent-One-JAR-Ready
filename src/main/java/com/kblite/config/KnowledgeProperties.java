package com.kblite.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 知识库参数配置（knowledge.*）
 *
 * @author kb-agent-lite
 */
@Data
@Component
@ConfigurationProperties(prefix = "knowledge")
public class KnowledgeProperties {

    private final Chunk chunk = new Chunk();
    private final Batch batch = new Batch();
    private final Rag rag = new Rag();

    @Data
    public static class Chunk {
        /** 切分块大小（字符） */
        private int size = 1000;
        /** 块重叠（字符） */
        private int overlap = 200;
    }

    @Data
    public static class Batch {
        /** 向量化批量大小 */
        private int size = 50;
    }

    @Data
    public static class Rag {
        /** 相似度阈值（relevance 标度 0~1） */
        private double minScore = 0.5;
        /** 粗召回扩展倍数 */
        private int recallExpandFactor = 5;
    }
}
