package com.kblite.knowledge.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.kblite.knowledge.entity.KnowledgeDocument;
import com.kblite.knowledge.model.vo.DocumentVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 知识库文档服务
 *
 * @author kb-agent-lite
 */
public interface KnowledgeDocumentService extends IService<KnowledgeDocument> {

    /**
     * 上传并创建文档（保存文件 → 建记录 → 触发异步向量化）
     */
    KnowledgeDocument createDocumentWithUpload(MultipartFile file, String categoryId,
                                               String title, String remark, String tags);

    /**
     * 分页查询文档列表
     */
    IPage<DocumentVO> getDocumentPage(Long current, Long size, String keyword,
                                      String categoryId, Integer vectorStatus);

    /**
     * 删除文档（向量 + 文件 + 记录 + 分类计数联动）
     */
    boolean deleteDocument(Long id);

    /**
     * 批量删除
     */
    boolean batchDeleteDocuments(List<Long> ids);

    /**
     * 打开文档原始文件流（预览/下载）
     */
    java.io.InputStream openDocumentStream(Long id);

    /**
     * 查询所有标签（去重）
     */
    List<String> getAllTags();

    /**
     * 查询存储配额使用情况（已用/上限/文档数）
     */
    java.util.Map<String, Object> getStorageQuota();
}
