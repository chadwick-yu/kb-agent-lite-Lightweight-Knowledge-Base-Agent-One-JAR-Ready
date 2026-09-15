package com.kblite.knowledge.service.impl;

import com.kblite.knowledge.entity.KnowledgeDocument;
import com.kblite.knowledge.model.DocumentParseResult;
import com.kblite.knowledge.model.SupportedFileType;
import com.kblite.knowledge.parser.DocumentParser;
import com.kblite.knowledge.parser.TikaDocumentParser;
import com.kblite.knowledge.service.DocumentParseService;
import com.kblite.knowledge.service.VectorStoreService;
import com.kblite.storage.LocalFileStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 文档解析服务实现
 *
 * @author kb-agent-lite
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentParseServiceImpl implements DocumentParseService {

    /** 单文件大小上限 50MB */
    private static final long MAX_FILE_SIZE = 50L * 1024 * 1024;

    private final List<DocumentParser> documentParsers;
    private final TikaDocumentParser tikaDocumentParser;
    private final VectorStoreService vectorStoreService;
    private final LocalFileStorage localFileStorage;

    @Override
    public DocumentParseResult parseAndNormalize(MultipartFile file) {
        String mimeType = detectMimeType(file);
        log.debug("开始解析文档: {}, MIME 类型: {}", file.getOriginalFilename(), mimeType);

        DocumentParser selectedParser = documentParsers.stream()
                .filter(parser -> parser.supports(mimeType))
                .findFirst()
                .orElse(tikaDocumentParser);

        DocumentParseResult result = selectedParser.parse(file);

        // 解析成功后进行文本标准化
        if (result.isSuccess()) {
            String normalizedContent = normalizeText(result.getContent());
            result.setContent(normalizedContent);
            log.info("文档解析并标准化完成: {}, 标准化后长度: {}",
                    file.getOriginalFilename(),
                    normalizedContent != null ? normalizedContent.length() : 0);
        }
        return result;
    }

    @Override
    public int vectorizeDocument(KnowledgeDocument document) {
        if (document == null || document.getFilePath() == null || document.getFilePath().trim().isEmpty()) {
            log.warn("文档文件路径为空，跳过向量化: {}", document != null ? document.getId() : "null");
            return 0;
        }
        String filePath = document.getFilePath();
        String documentId = document.getDocumentId();

        log.info("开始对已有文档进行向量化: DB ID={}, documentId={}, filePath={}",
                document.getId(), documentId, filePath);

        try (InputStream inputStream = localFileStorage.open(filePath)) {
            byte[] fileBytes = inputStream.readAllBytes();

            MultipartFile multipartFile = new ByteArrayMultipartFile(
                    document.getOriginalFileName(),
                    document.getFileType(),
                    document.getFileSize() != null ? document.getFileSize() : fileBytes.length,
                    fileBytes
            );

            DocumentParseResult result = parseAndNormalize(multipartFile);
            if (!result.isSuccess()) {
                log.error("文档解析失败: {}, 原因: {}", filePath, result.getErrorMessage());
                throw new RuntimeException("文档解析失败: " + result.getErrorMessage());
            }

            Map<String, String> metadata = buildMetadata(result, filePath, document.getCategoryName());
            int vectorCount = vectorStoreService.storeDocument(documentId, result.getContent(), metadata);

            log.info("文档向量化完成: DB ID={}, documentId={}, 向量数={}",
                    document.getId(), documentId, vectorCount);
            return vectorCount;

        } catch (IOException e) {
            throw new RuntimeException("文档向量化失败: " + e.getMessage(), e);
        }
    }

    @Override
    public List<VectorStoreService.SimilarDocument> searchSimilarDocuments(String query, int maxResults) {
        return vectorStoreService.searchSimilarDocuments(query, maxResults);
    }

    @Override
    public void validateFileType(MultipartFile file) {
        try {
            String mimeType = detectMimeType(file);
            if (!SupportedFileType.isSupported(mimeType)) {
                throw new IllegalArgumentException(
                        String.format("不支持的文件格式: %s (MIME: %s)。支持: pdf/word/excel/ppt/md/txt/rtf",
                                file.getOriginalFilename(), mimeType));
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("无法识别文件类型: " + file.getOriginalFilename());
        }
    }

    @Override
    public void validateFileSize(MultipartFile file) {
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException(String.format(
                    "文件大小超过限制: %s (%.2fMB)，最大允许 50MB",
                    file.getOriginalFilename(), file.getSize() / 1024.0 / 1024.0));
        }
    }

    private Map<String, String> buildMetadata(DocumentParseResult result, String filePath, String categoryName) {
        Map<String, String> metadata = new HashMap<>();
        metadata.put("originalFileName", result.getOriginalFileName());
        metadata.put("fileType", result.getFileType());
        metadata.put("filePath", filePath);
        if (categoryName != null) {
            metadata.put("categoryName", categoryName);
        }
        if (result.getMetadata() != null) {
            metadata.putAll(result.getMetadata());
        }
        return metadata;
    }

    @Override
    public String resolveFileType(MultipartFile file) {
        return detectMimeType(file);
    }

    private String detectMimeType(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType == null || contentType.trim().isEmpty() || "application/octet-stream".equals(contentType)) {
            // 浏览器对 .md 等扩展名可能上报 octet-stream：用 Tika 按文件内容嗅探真实类型
            try (InputStream in = file.getInputStream()) {
                String sniffed = new org.apache.tika.Tika().detect(in, file.getOriginalFilename());
                if (sniffed != null && !sniffed.isBlank()) {
                    return sniffed;
                }
            } catch (IOException e) {
                log.warn("Tika 类型嗅探失败: {}, 原因: {}", file.getOriginalFilename(), e.getMessage());
            }
            return contentType == null ? "" : contentType;
        }
        return contentType;
    }

    /**
     * 文本标准化处理
     * 去控制字符/特殊空白字符、统一换行、合并连续空行
     */
    private String normalizeText(String text) {
        if (text == null || text.trim().isEmpty()) {
            return text;
        }
        // 1. 去除不可见控制字符（保留换行与制表符）
        text = text.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]", "");
        // 2. 去除特殊空白字符（全角空格/零宽空格等）
        text = text.replaceAll("[\\u00A0\\u200B\\u200C\\u200D\\uFEFF]", " ");
        // 3. 统一换行
        text = text.replaceAll("\\r\\n?", "\\n");
        // 4. 按行处理：去首尾空格、合并连续空行
        String[] lines = text.split("\\n");
        StringBuilder result = new StringBuilder(text.length());
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                if (result.length() > 0 && result.charAt(result.length() - 1) != '\n') {
                    result.append("\n");
                }
                continue;
            }
            if (result.length() > 0 && result.charAt(result.length() - 1) == '\n') {
                result.append(trimmed);
            } else if (result.length() > 0) {
                result.append("\n").append(trimmed);
            } else {
                result.append(trimmed);
            }
        }
        return result.toString().replaceAll("\\n+$", "");
    }

    /**
     * 基于字节数组的 MultipartFile 适配器（供本地文件重新解析使用）
     */
    private static class ByteArrayMultipartFile implements MultipartFile {
        private final String originalFilename;
        private final String contentType;
        private final long size;
        private final byte[] content;

        ByteArrayMultipartFile(String originalFilename, String contentType, long size, byte[] content) {
            this.originalFilename = originalFilename;
            this.contentType = contentType;
            this.size = size;
            this.content = content;
        }

        @Override
        public String getName() {
            return originalFilename;
        }

        @Override
        public String getOriginalFilename() {
            return originalFilename;
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public boolean isEmpty() {
            return content == null || content.length == 0;
        }

        @Override
        public long getSize() {
            return size;
        }

        @Override
        public byte[] getBytes() {
            return content;
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(content);
        }

        @Override
        public void transferTo(File dest) {
            throw new UnsupportedOperationException("transferTo 不支持");
        }
    }
}
