package com.kblite.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kblite.knowledge.entity.KnowledgeCategory;
import com.kblite.config.KnowledgeProperties;
import com.kblite.knowledge.entity.KnowledgeDocument;
import com.kblite.knowledge.exception.DocumentNotFoundException;
import com.kblite.knowledge.mapper.KnowledgeCategoryMapper;
import com.kblite.knowledge.mapper.KnowledgeDocumentMapper;
import com.kblite.knowledge.model.vo.DocumentVO;
import com.kblite.knowledge.service.AsyncDocumentService;
import com.kblite.knowledge.service.DocumentParseService;
import com.kblite.knowledge.service.KnowledgeDocumentService;
import com.kblite.knowledge.service.VectorStoreService;
import com.kblite.storage.LocalFileStorage;
import com.kblite.common.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 知识库文档服务实现
 *
 * @author kb-agent-lite
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeDocumentServiceImpl extends ServiceImpl<KnowledgeDocumentMapper, KnowledgeDocument>
        implements KnowledgeDocumentService {

    private static final String DEFAULT_USER = "admin";
    private static final int MAX_PAGE_SIZE = 100;

    private final KnowledgeProperties knowledgeProperties;
    private final VectorStoreService vectorStoreService;
    private final LocalFileStorage localFileStorage;
    private final KnowledgeCategoryMapper categoryMapper;
    private final DocumentParseService documentParseService;
    private final AsyncDocumentService asyncDocumentService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeDocument createDocumentWithUpload(MultipartFile file, String categoryId,
                                                      String title, String remark, String tags) {
        // 1. 校验（格式 / 单文件大小 / 总量配额）
        documentParseService.validateFileType(file);
        documentParseService.validateFileSize(file);
        checkUploadQuota(file.getSize());

        // 2. 查询真实分类名称（用于向量元数据展示）
        String realCategoryName = "KNOWLEDGE";
        if (StringUtils.hasText(categoryId)) {
            KnowledgeCategory category = categoryMapper.selectById(Long.valueOf(categoryId));
            if (category != null) {
                realCategoryName = category.getCategoryName();
            }
        }

        // 3. 保存原始文件到本地磁盘
        String filePath = localFileStorage.save(file, realCategoryName);

        // 4. 建库记录（向量化状态=处理中）
        String documentId = UUID.randomUUID().toString().replace("-", "");
        KnowledgeDocument document = new KnowledgeDocument();
        document.setDocumentId(documentId);
        document.setTitle(StringUtils.hasText(title) ? title : file.getOriginalFilename());
        document.setOriginalFileName(file.getOriginalFilename());
        document.setFileType(documentParseService.resolveFileType(file));
        document.setFileSize(file.getSize());
        document.setFilePath(filePath);
        document.setCategoryId(categoryId);
        document.setCategoryName(realCategoryName);
        String originalFileName = file.getOriginalFilename();
        if (originalFileName != null && originalFileName.contains(".")) {
            document.setFileExtension(originalFileName.substring(originalFileName.lastIndexOf(".") + 1));
        }
        document.setStatus(1);
        document.setVectorStatus(1);
        document.setRemark(remark);
        document.setTags(tags);
        Date now = new Date();
        document.setCreateTime(now);
        document.setUpdateTime(now);
        document.setCreateUser(DEFAULT_USER);
        document.setUpdateUser(DEFAULT_USER);
        document.setUserId(DEFAULT_USER);
        save(document);
        log.info("创建文档记录成功: DB ID={}, documentId={}, file={}, vectorStatus=1(处理中)",
                document.getId(), documentId, originalFileName);

        // 5. 更新分类文档数量
        if (StringUtils.hasText(categoryId)) {
            updateCategoryDocumentCount(categoryId, 1);
        }

        // 6. 异步解析 + 向量化
        asyncDocumentService.parseAndVectorize(document.getId());

        return document;
    }

    @Override
    public IPage<DocumentVO> getDocumentPage(Long current, Long size, String keyword,
                                             String categoryId, Integer vectorStatus) {
        LambdaQueryWrapper<KnowledgeDocument> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(KnowledgeDocument::getStatus, 1);
        if (StringUtils.hasText(categoryId)) {
            queryWrapper.eq(KnowledgeDocument::getCategoryId, categoryId);
        }
        if (vectorStatus != null) {
            queryWrapper.eq(KnowledgeDocument::getVectorStatus, vectorStatus);
        }
        if (StringUtils.hasText(keyword)) {
            queryWrapper.and(wrapper -> wrapper
                    .like(KnowledgeDocument::getTitle, keyword)
                    .or()
                    .like(KnowledgeDocument::getOriginalFileName, keyword));
        }
        queryWrapper.orderByDesc(KnowledgeDocument::getCreateTime);

        long cur = current == null ? 1L : Math.max(1L, current);
        long sz = size == null ? 10L : Math.max(1L, Math.min(MAX_PAGE_SIZE, size));
        IPage<KnowledgeDocument> documentPage = page(new Page<>(cur, sz), queryWrapper);
        return documentPage.convert(DocumentVO::from);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteDocument(Long id) {
        KnowledgeDocument document = getById(id);
        if (document == null) {
            throw new DocumentNotFoundException("文档不存在");
        }

        // 1. 删除向量数据
        try {
            vectorStoreService.deleteDocument(document.getDocumentId());
            log.info("删除向量数据完成: {}", document.getDocumentId());
        } catch (Exception e) {
            log.error("删除向量数据异常: {}", document.getDocumentId(), e);
        }

        // 2. 删除本地文件
        try {
            localFileStorage.delete(document.getFilePath());
        } catch (Exception e) {
            log.error("删除本地文件异常: {}", document.getFilePath(), e);
        }

        // 3. 删除数据库记录
        removeById(id);

        // 4. 更新分类文档数量
        if (StringUtils.hasText(document.getCategoryId())) {
            updateCategoryDocumentCount(document.getCategoryId(), -1);
        }

        log.info("删除文档成功: {}", id);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean batchDeleteDocuments(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return false;
        }
        for (Long id : ids) {
            try {
                deleteDocument(id);
            } catch (Exception e) {
                log.error("批量删除文档失败，ID: {}", id, e);
            }
        }
        return true;
    }

    @Override
    public InputStream openDocumentStream(Long id) {
        KnowledgeDocument document = getById(id);
        if (document == null) {
            throw new DocumentNotFoundException("文档不存在");
        }
        if (!StringUtils.hasText(document.getFilePath())) {
            throw new BizException("文档尚未上传，无法预览");
        }
        return localFileStorage.open(document.getFilePath());
    }

    /**
     * 上传总量配额校验：已用空间 + 待传文件 超过上限则拒绝
     */
    private void checkUploadQuota(long incomingSize) {
        com.kblite.config.KnowledgeProperties.Upload up = knowledgeProperties.getUpload();
        if (up.getMaxDocCount() > 0) {
            long count = lambdaQuery().eq(KnowledgeDocument::getStatus, 1).count();
            if (count >= up.getMaxDocCount()) {
                throw new IllegalArgumentException(String.format(
                        "知识库文档数量已达上限（%d/%d 篇），请先删除部分文档再上传",
                        count, up.getMaxDocCount()));
            }
        }
        if (up.getTotalSizeMb() > 0) {
            long usedBytes = currentUsedBytes();
            long quotaBytes = up.getTotalSizeMb() * 1024L * 1024L;
            if (usedBytes + incomingSize > quotaBytes) {
                throw new IllegalArgumentException(String.format(
                        "知识库总容量已达上限：已用 %.1fMB / 上限 %dMB，本次需 %.1fMB，请先删除部分文档再上传",
                        usedBytes / 1024.0 / 1024.0, up.getTotalSizeMb(), incomingSize / 1024.0 / 1024.0));
            }
        }
    }

    private long currentUsedBytes() {
        List<KnowledgeDocument> docs = lambdaQuery()
                .eq(KnowledgeDocument::getStatus, 1)
                .select(KnowledgeDocument::getFileSize)
                .list();
        return docs.stream().mapToLong(d -> d.getFileSize() == null ? 0L : d.getFileSize()).sum();
    }

    @Override
    public Map<String, Object> getStorageQuota() {
        com.kblite.config.KnowledgeProperties.Upload up = knowledgeProperties.getUpload();
        long usedBytes = currentUsedBytes();
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("usedBytes", usedBytes);
        m.put("quotaBytes", up.getTotalSizeMb() > 0 ? up.getTotalSizeMb() * 1024L * 1024L : 0);
        m.put("docCount", lambdaQuery().eq(KnowledgeDocument::getStatus, 1).count());
        m.put("maxDocCount", up.getMaxDocCount());
        return m;
    }

    @Override
    public List<String> getAllTags() {
        LambdaQueryWrapper<KnowledgeDocument> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KnowledgeDocument::getStatus, 1)
                .isNotNull(KnowledgeDocument::getTags)
                .ne(KnowledgeDocument::getTags, "");
        List<KnowledgeDocument> documents = list(wrapper);
        Set<String> tagSet = new LinkedHashSet<>();
        for (KnowledgeDocument doc : documents) {
            if (StringUtils.hasText(doc.getTags())) {
                Arrays.stream(doc.getTags().split(","))
                        .map(String::trim)
                        .filter(StringUtils::hasText)
                        .forEach(tagSet::add);
            }
        }
        return new ArrayList<>(tagSet);
    }

    private void updateCategoryDocumentCount(String categoryId, int delta) {
        if (!StringUtils.hasText(categoryId)) {
            return;
        }
        KnowledgeCategory category = categoryMapper.selectById(Long.valueOf(categoryId));
        if (category != null) {
            category.setDocumentCount(Math.max(0, (category.getDocumentCount() == null ? 0 : category.getDocumentCount()) + delta));
            categoryMapper.updateById(category);
        }
    }
}
