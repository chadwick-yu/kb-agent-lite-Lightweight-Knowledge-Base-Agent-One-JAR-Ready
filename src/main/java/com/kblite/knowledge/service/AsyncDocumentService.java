package com.kblite.knowledge.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kblite.knowledge.entity.KnowledgeDocument;
import com.kblite.knowledge.mapper.KnowledgeDocumentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 文档异步解析向量化服务
 *
 * @author kb-agent-lite
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AsyncDocumentService extends ServiceImpl<KnowledgeDocumentMapper, KnowledgeDocument> {

    private final DocumentParseService documentParseService;

    /**
     * 异步对已上传文档进行解析和向量化，回写 vectorStatus：2-成功 3-失败
     */
    @Async("knowledgeAsyncExecutor")
    public void parseAndVectorize(Long documentId) {
        log.info("开始异步解析和向量化: documentId={}", documentId);
        try {
            KnowledgeDocument document = getById(documentId);
            if (document == null) {
                log.error("异步向量化失败: 文档不存在, documentId={}", documentId);
                return;
            }

            int vectorCount = documentParseService.vectorizeDocument(document);

            KnowledgeDocument update = new KnowledgeDocument();
            update.setId(documentId);
            update.setVectorStatus(2);
            update.setVectorCount(vectorCount);
            update.setContentLength(document.getContentLength());
            updateById(update);
            log.info("异步向量化完成: documentId={}, 向量数={}", documentId, vectorCount);

        } catch (Exception e) {
            updateVectorStatus(documentId, 3, e.getMessage());
            log.error("异步向量化异常: documentId={}", documentId, e);
        }
    }

    private void updateVectorStatus(Long documentId, int status, String error) {
        try {
            KnowledgeDocument update = new KnowledgeDocument();
            update.setId(documentId);
            update.setVectorStatus(status);
            if (error != null) {
                update.setVectorError(error.length() > 500 ? error.substring(0, 500) : error);
            }
            updateById(update);
        } catch (Exception e) {
            log.error("更新向量化状态失败: documentId={}", documentId, e);
        }
    }
}
