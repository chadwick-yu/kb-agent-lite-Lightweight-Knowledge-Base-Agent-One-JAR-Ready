package com.kblite.chat.tools;

import com.kblite.config.KnowledgeProperties;
import com.kblite.knowledge.service.DocumentParseService;
import com.kblite.knowledge.service.VectorStoreService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识库检索工具（RAG 粗召回 → 阈值过滤 → 去重 → 精选）
 * RAG 策略：粗召回(limit×factor) → 阈值过滤 → 同文档去重 → top-N
 *
 * @author kb-agent-lite
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeTools {

    private final DocumentParseService documentParseService;
    private final KnowledgeProperties knowledgeProperties;

    @Tool("搜索知识库文档，查找技术文档、操作手册、规范标准等知识内容。" +
            "当用户询问操作方法、技术规范、安装指南、配置说明、故障排查等知识类问题时调用。" +
            "搜索结果会包含文档所属分类与来源文件信息。")
    public String searchKnowledge(
            @P("用户的问题或搜索关键词，必填") String query,
            @P("返回结果数量，可选。默认3条，最大5条") Integer maxResults) {
        log.info("[链路-知识库] >>> searchKnowledge 开始 - query: {}, maxResults: {}", query, maxResults);
        long startMs = System.currentTimeMillis();
        try {
            int finalLimit = (maxResults != null && maxResults > 0) ? Math.min(maxResults, 5) : 3;
            int recallExpandFactor = knowledgeProperties.getRag().getRecallExpandFactor();
            double minScoreThreshold = knowledgeProperties.getRag().getMinScore();

            // 粗召回 → 阈值过滤 → 同文档去重 → 精选
            int recallLimit = finalLimit * recallExpandFactor;
            List<VectorStoreService.SimilarDocument> rawDocs =
                    documentParseService.searchSimilarDocuments(query, recallLimit);
            if (rawDocs == null || rawDocs.isEmpty()) {
                return "知识库中未找到与该问题相关的文档内容，请根据已有信息回答用户，并提示用户可上传相关设备文档。";
            }

            // 相似度阈值过滤
            List<VectorStoreService.SimilarDocument> filteredDocs = new ArrayList<>();
            for (VectorStoreService.SimilarDocument doc : rawDocs) {
                if (doc.getScore() >= minScoreThreshold) {
                    filteredDocs.add(doc);
                }
            }
            if (filteredDocs.isEmpty()) {
                return "知识库中未找到高度相关的文档内容，请根据已有信息回答用户，并明确告知这是参考信息。";
            }

            // 同文档去重（每组只保留最高分 chunk）
            Map<String, VectorStoreService.SimilarDocument> dedupedMap = new LinkedHashMap<>();
            for (VectorStoreService.SimilarDocument doc : filteredDocs) {
                String docId = doc.getDocumentId();
                VectorStoreService.SimilarDocument existing = dedupedMap.get(docId);
                if (existing == null || doc.getScore() > existing.getScore()) {
                    dedupedMap.put(docId, doc);
                }
            }

            // 排序 + top-N
            List<VectorStoreService.SimilarDocument> selectedDocs = new ArrayList<>(dedupedMap.values());
            selectedDocs.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
            if (selectedDocs.size() > finalLimit) {
                selectedDocs = new ArrayList<>(selectedDocs.subList(0, finalLimit));
            }

            // 写入引用来源上下文（供 SSE sources 事件使用）
            List<SearchContext.SourceInfo> sources = new ArrayList<>();
            for (VectorStoreService.SimilarDocument doc : selectedDocs) {
                Map<String, Object> metadata = doc.getMetadata();
                String fileName = metadata != null
                        ? String.valueOf(metadata.getOrDefault("originalFileName", "未知文件")) : "未知文件";
                String category = metadata != null
                        ? String.valueOf(metadata.getOrDefault("categoryName", "")) : "";
                sources.add(new SearchContext.SourceInfo(fileName, category, doc.getScore()));
            }
            SearchContext.setSources(sources);

            log.info("[链路-知识库] <<< searchKnowledge 完成 - 耗时: {}ms, 粗召回{}条 → 阈值过滤{}条 → 去重{}条 → 精选{}条",
                    System.currentTimeMillis() - startMs, rawDocs.size(), filteredDocs.size(),
                    dedupedMap.size(), selectedDocs.size());

            StringBuilder sb = new StringBuilder("知识库检索结果（共找到 " + selectedDocs.size() + " 条相关内容）：\n\n");
            int idx = 0;
            for (VectorStoreService.SimilarDocument doc : selectedDocs) {
                idx++;
                sb.append("--- 相关文档 ").append(idx).append(" ---\n");
                sb.append("相似度分数：").append(String.format("%.2f", doc.getScore())).append("\n");
                Map<String, Object> metadata = doc.getMetadata();
                if (metadata != null) {
                    Object categoryName = metadata.get("categoryName");
                    Object fileName = metadata.get("originalFileName");
                    if (categoryName != null) {
                        sb.append("所属分类：").append(categoryName).append("\n");
                    }
                    if (fileName != null) {
                        sb.append("来源文件：").append(fileName).append("\n");
                    }
                }
                sb.append("文档内容：").append(doc.getContent()).append("\n\n");
            }
            sb.append("请基于以上知识库内容回答用户问题，并在涉及处标注来源文件名。");
            return sb.toString();
        } catch (Exception e) {
            log.error("[链路-知识库] searchKnowledge 异常 - query: {}, 耗时: {}ms, error: {}",
                    query, System.currentTimeMillis() - startMs, e.getMessage(), e);
            return "知识库检索出现异常，请告知用户检索出现问题，请稍后重试。";
        }
    }
}
