package com.kblite.knowledge.parser;

import com.kblite.knowledge.exception.DocumentParseException;
import com.kblite.knowledge.exception.UnsupportedFileTypeException;
import com.kblite.knowledge.model.DocumentParseResult;
import com.kblite.knowledge.model.SupportedFileType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.xml.sax.ContentHandler;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 基于 Apache Tika 的文档解析器
 * 支持 PDF、Word、Excel、PPT、Markdown、TXT、RTF 等格式
 *
 * @author kb-agent-lite
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TikaDocumentParser implements DocumentParser {

    private final Parser parser;

    private final org.apache.tika.Tika tika = new org.apache.tika.Tika();

    /** 最大文本长度限制（10MB），防止超大文件 OOM */
    private static final int MAX_TEXT_LENGTH = 10_000_000;

    @Override
    public DocumentParseResult parse(MultipartFile file) {
        String mimeType = detectMimeType(file);
        if (!supports(mimeType)) {
            throw new UnsupportedFileTypeException("不支持的文件类型: " + mimeType);
        }

        long startTime = System.currentTimeMillis();
        try (InputStream inputStream = file.getInputStream()) {
            ContentHandler handler = new BodyContentHandler(MAX_TEXT_LENGTH);
            Metadata metadata = new Metadata();
            metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, file.getOriginalFilename());

            parser.parse(inputStream, handler, metadata, new ParseContext());

            String content = handler.toString();
            long duration = System.currentTimeMillis() - startTime;
            log.info("文档解析成功: {}, 文件大小: {} bytes, 文本长度: {}, MIME: {}, 耗时: {} ms",
                    file.getOriginalFilename(), file.getSize(), content.length(), mimeType, duration);

            return DocumentParseResult.builder()
                    .documentId(UUID.randomUUID().toString())
                    .originalFileName(file.getOriginalFilename())
                    .fileType(mimeType)
                    .fileSize(file.getSize())
                    .content(content)
                    .metadata(extractMetadata(metadata))
                    .parseTime(LocalDateTime.now())
                    .success(true)
                    .build();

        } catch (UnsupportedFileTypeException e) {
            throw e;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("文档解析失败: {}, 耗时: {} ms", file.getOriginalFilename(), duration, e);
            return DocumentParseResult.builder()
                    .documentId(UUID.randomUUID().toString())
                    .originalFileName(file.getOriginalFilename())
                    .fileType(mimeType)
                    .fileSize(file.getSize())
                    .success(false)
                    .errorMessage("文档解析失败: " + e.getMessage())
                    .parseTime(LocalDateTime.now())
                    .build();
        }
    }

    @Override
    public boolean supports(String mimeType) {
        return SupportedFileType.isSupported(mimeType);
    }

    private String detectMimeType(MultipartFile file) {
        try (InputStream inputStream = file.getInputStream()) {
            return tika.detect(inputStream, file.getOriginalFilename());
        } catch (Exception e) {
            throw new DocumentParseException("无法检测文件类型: " + e.getMessage(), e);
        }
    }

    private Map<String, String> extractMetadata(Metadata metadata) {
        Map<String, String> metaMap = new HashMap<>();
        String[] keyFields = {
                "title", "Author", "Creation-Date", "Last-Modified",
                "Keywords", "description", "Content-Type", "Content-Length"
        };
        for (String name : metadata.names()) {
            String value = metadata.get(name);
            if (value != null && !value.trim().isEmpty()) {
                for (String keyField : keyFields) {
                    if (name.equals(keyField) || name.contains(keyField)) {
                        metaMap.put(name, value);
                        break;
                    }
                }
            }
        }
        return metaMap;
    }
}
