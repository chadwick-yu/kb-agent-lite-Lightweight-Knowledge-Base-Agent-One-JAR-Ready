package com.kblite.knowledge.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.kblite.common.ApiResult;
import com.kblite.knowledge.model.vo.DocumentVO;
import com.kblite.knowledge.service.KnowledgeDocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 知识库文档管理接口
 *
 * @author kb-agent-lite
 */
@Slf4j
@RestController
@RequestMapping("/api/knowledge/document")
@RequiredArgsConstructor
public class KnowledgeDocumentController {

    private final KnowledgeDocumentService knowledgeDocumentService;

    /**
     * 上传文档（支持多文件），保存后触发异步向量化
     */
    @PostMapping
    public ApiResult<List<DocumentVO>> upload(
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam(value = "categoryId", required = false) String categoryId,
            @RequestParam(value = "tags", required = false) String tags,
            @RequestParam(value = "remark", required = false) String remark) {
        if (files == null || files.isEmpty()) {
            return ApiResult.fail(400, "请选择要上传的文件");
        }
        List<DocumentVO> result = files.stream()
                .map(file -> DocumentVO.from(
                        knowledgeDocumentService.createDocumentWithUpload(file, categoryId, null, remark, tags)))
                .toList();
        return ApiResult.ok(result);
    }

    /**
     * 文档分页列表
     */
    @GetMapping("/list")
    public ApiResult<IPage<DocumentVO>> list(
            @RequestParam(value = "current", required = false, defaultValue = "1") Long current,
            @RequestParam(value = "size", required = false, defaultValue = "10") Long size,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "categoryId", required = false) String categoryId,
            @RequestParam(value = "vectorStatus", required = false) Integer vectorStatus) {
        return ApiResult.ok(knowledgeDocumentService.getDocumentPage(
                current, size, keyword, categoryId, vectorStatus));
    }

    /**
     * 文档预览/下载（原始文件流，前端可用 ?token= 直链访问）
     */
    @GetMapping("/{id}/preview")
    public ResponseEntity<InputStreamResource> preview(@PathVariable("id") Long id) {
        com.kblite.knowledge.entity.KnowledgeDocument doc = knowledgeDocumentService.getById(id);
        if (doc == null) {
            return ResponseEntity.notFound().build();
        }
        String fileName = doc.getOriginalFileName();
        String encoded = URLEncoder.encode(fileName == null ? "file" : fileName, StandardCharsets.UTF_8)
                .replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename*=UTF-8''" + encoded)
                .contentType(MediaType.parseMediaType(
                        doc.getFileType() == null ? "application/octet-stream" : doc.getFileType()))
                .body(new InputStreamResource(knowledgeDocumentService.openDocumentStream(id)));
    }

    /**
     * 删除文档（联动删除向量与文件）
     */
    @DeleteMapping("/{id}")
    public ApiResult<Boolean> delete(@PathVariable("id") Long id) {
        return ApiResult.ok(knowledgeDocumentService.deleteDocument(id));
    }

    /**
     * 批量删除
     */
    @PostMapping("/batch-delete")
    public ApiResult<Boolean> batchDelete(@RequestBody Map<String, List<Long>> body) {
        List<Long> ids = body == null ? null : body.get("ids");
        return ApiResult.ok(knowledgeDocumentService.batchDeleteDocuments(ids));
    }

    /**
     * 存储配额查询（已用/上限/文档数）
     */
    @GetMapping("/quota")
    public ApiResult<Map<String, Object>> quota() {
        return ApiResult.ok(knowledgeDocumentService.getStorageQuota());
    }

    /**
     * 所有标签（去重）
     */
    @GetMapping("/tags")
    public ApiResult<List<String>> tags() {
        return ApiResult.ok(knowledgeDocumentService.getAllTags());
    }
}
