package com.kblite.knowledge.service.impl;

import com.kblite.config.AppProperties;
import com.kblite.config.KnowledgeProperties;
import com.kblite.knowledge.service.VectorStoreService;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 内嵌向量存储服务实现
 * langchain4j InMemoryEmbeddingStore（内存暴力检索，几万块规模毫秒级）
 * + JSON 序列化持久化到 {dataDir}/vectors.json（临时文件 + 原子替换，防止写坏）
 *
 * 知识块（文本 + 向量 + 元数据）只存在于向量库中，不落关系库
 *
 * @author kb-agent-lite
 */
@Slf4j
@Service
public class FileVectorStoreServiceImpl implements VectorStoreService {

    private final EmbeddingModel embeddingModel;
    private final KnowledgeProperties knowledgeProperties;
    private final Path persistPath;

    /** 内存向量库（启动时从持久化文件加载） */
    private final InMemoryEmbeddingStore<TextSegment> embeddingStore;

    /** 读写锁对象：向量库变更 + 持久化串行化 */
    private final Object storeLock = new Object();

    @Autowired
    public FileVectorStoreServiceImpl(EmbeddingModel embeddingModel,
                                      KnowledgeProperties knowledgeProperties,
                                      AppProperties appProperties) {
        this.embeddingModel = embeddingModel;
        this.knowledgeProperties = knowledgeProperties;
        this.persistPath = java.nio.file.Paths.get(appProperties.getDataDir())
                .toAbsolutePath().normalize().resolve("vectors.json");
        this.embeddingStore = loadFromDisk();
        log.info("[VectorStore] 内嵌向量库初始化完成, 持久化文件: {}", persistPath);
    }

    private InMemoryEmbeddingStore<TextSegment> loadFromDisk() {
        if (Files.exists(persistPath)) {
            try {
                InMemoryEmbeddingStore<TextSegment> store =
                        InMemoryEmbeddingStore.fromJson(Files.readString(persistPath));
                log.info("[VectorStore] 已从磁盘加载向量库: {}", persistPath);
                return store;
            } catch (Exception e) {
                log.error("[VectorStore] 向量库文件加载失败（将使用空库，可通过重新上传文档重建）: {}", e.getMessage(), e);
            }
        }
        return new InMemoryEmbeddingStore<>();
    }

    @PreDestroy
    public void persistOnShutdown() {
        persist();
    }

    @Override
    public int storeDocument(String documentId, String content, Map<String, String> metadata) {
        if (content == null || content.trim().isEmpty()) {
            log.warn("文档内容为空，跳过存储: {}", documentId);
            return 0;
        }

        long startTime = System.currentTimeMillis();
        int chunkSize = knowledgeProperties.getChunk().getSize();
        int chunkOverlap = knowledgeProperties.getChunk().getOverlap();
        int batchSize = knowledgeProperties.getBatch().getSize();

        try {
            log.info("开始存储文档向量: {}, 内容长度: {}, 分块大小: {}, 重叠: {}",
                    documentId, content.length(), chunkSize, chunkOverlap);

            // 1. 文档分块（递归切分）
            DocumentSplitter splitter = DocumentSplitters.recursive(chunkSize, chunkOverlap);
            List<TextSegment> segments = splitter.split(Document.from(content));
            if (segments.isEmpty()) {
                log.warn("文档分块结果为空: {}", documentId);
                return 0;
            }
            log.info("文档分块完成: {}, 共 {} 个块", documentId, segments.size());

            // 2. 批量向量化 + 逐条入库（错误隔离：单块失败不中断整篇）
            int storedCount = 0;
            int total = segments.size();
            for (int i = 0; i < total; i += batchSize) {
                int end = Math.min(i + batchSize, total);
                List<TextSegment> batch = segments.subList(i, end);
                storedCount += processBatch(documentId, batch, metadata, i, total);
            }

            // 3. 持久化到磁盘
            persist();

            log.info("文档向量存储完成: {}, 存储 {}/{} 个向量块, 耗时: {} ms",
                    documentId, storedCount, total, System.currentTimeMillis() - startTime);
            return storedCount;

        } catch (Exception e) {
            log.error("存储文档向量失败: {}, 耗时: {} ms", documentId, System.currentTimeMillis() - startTime, e);
            throw new RuntimeException("存储文档向量失败: " + e.getMessage(), e);
        }
    }

    private int processBatch(String documentId, List<TextSegment> batchSegments,
                             Map<String, String> metadata, int startIndex, int totalChunks) {
        int stored = 0;
        for (int i = 0; i < batchSegments.size(); i++) {
            TextSegment segment = batchSegments.get(i);
            int globalIndex = startIndex + i;

            Map<String, String> segmentMetadata = new HashMap<>();
            if (metadata != null) {
                segmentMetadata.putAll(metadata);
            }
            segmentMetadata.put("documentId", documentId);
            segmentMetadata.put("chunkIndex", String.valueOf(globalIndex));
            segmentMetadata.put("totalChunks", String.valueOf(totalChunks));

            TextSegment enriched = TextSegment.from(segment.text(), Metadata.from(segmentMetadata));
            try {
                Embedding embedding = embeddingModel.embed(enriched).content();
                synchronized (storeLock) {
                    embeddingStore.add(embedding, enriched);
                }
                stored++;
            } catch (Exception e) {
                log.error("向量化/存储失败: 文档 {}, 块索引 {}", documentId, globalIndex, e);
            }
        }
        return stored;
    }

    @Override
    public List<SimilarDocument> searchSimilarDocuments(String query, int maxResults) {
        if (query == null || query.trim().isEmpty()) {
            log.warn("查询文本为空，无法搜索");
            return new ArrayList<>();
        }
        long startTime = System.currentTimeMillis();
        try {
            Embedding queryEmbedding = embeddingModel.embed(query).content();
            EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                    .queryEmbedding(queryEmbedding)
                    .maxResults(Math.max(1, maxResults))
                    .build();

            List<SimilarDocument> results = new ArrayList<>();
            EmbeddingSearchResult<TextSegment> searchResult;
            synchronized (storeLock) {
                searchResult = embeddingStore.search(request);
            }
            for (EmbeddingMatch<TextSegment> match : searchResult.matches()) {
                TextSegment segment = match.embedded();
                if (segment == null) {
                    continue;
                }
                Map<String, Object> metadataMap = new HashMap<>(segment.metadata().toMap());
                String docId = String.valueOf(metadataMap.getOrDefault("documentId", "unknown"));
                results.add(new SimilarDocument(docId, segment.text(), match.score(), metadataMap));
            }
            log.info("向量搜索完成: query='{}', 命中 {} 条, 耗时: {} ms",
                    query, results.size(), System.currentTimeMillis() - startTime);
            return results;
        } catch (Exception e) {
            log.error("搜索相似文档失败, 耗时: {} ms", System.currentTimeMillis() - startTime, e);
            throw new RuntimeException("搜索相似文档失败: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean deleteDocument(String documentId) {
        try {
            Filter filter = MetadataFilterBuilder.metadataKey("documentId").isEqualTo(documentId);
            synchronized (storeLock) {
                embeddingStore.removeAll(filter);
            }
            persist();
            log.info("文档向量删除成功: {}", documentId);
            return true;
        } catch (Exception e) {
            log.error("删除文档向量失败: {}", documentId, e);
            return false;
        }
    }

    /**
     * 持久化向量库：序列化为 JSON 后写入临时文件，再原子替换，防止写入中断导致文件损坏
     */
    private void persist() {
        synchronized (storeLock) {
            try {
                Files.createDirectories(persistPath.getParent());
                Path tmp = persistPath.resolveSibling(persistPath.getFileName() + ".tmp");
                Files.writeString(tmp, embeddingStore.serializeToJson());
                Files.move(tmp, persistPath, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
                log.debug("[VectorStore] 向量库已持久化: {} ({} bytes)", persistPath, Files.size(persistPath));
            } catch (IOException e) {
                log.error("[VectorStore] 向量库持久化失败: {}", e.getMessage(), e);
            }
        }
    }
}
