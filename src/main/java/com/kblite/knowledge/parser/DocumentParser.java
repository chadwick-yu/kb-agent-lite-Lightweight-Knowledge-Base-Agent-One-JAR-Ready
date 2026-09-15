package com.kblite.knowledge.parser;

import com.kblite.knowledge.model.DocumentParseResult;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文档解析器接口
 *
 * @author kb-agent-lite
 */
public interface DocumentParser {

    DocumentParseResult parse(MultipartFile file);

    boolean supports(String mimeType);
}
