package com.kblite.knowledge.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kblite.common.BizException;
import com.kblite.knowledge.entity.KnowledgeCategory;
import com.kblite.knowledge.mapper.KnowledgeCategoryMapper;
import com.kblite.knowledge.service.KnowledgeCategoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 知识库分类服务实现
 *
 * @author kb-agent-lite
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeCategoryServiceImpl extends ServiceImpl<KnowledgeCategoryMapper, KnowledgeCategory>
        implements KnowledgeCategoryService {

    private static final String DEFAULT_USER = "admin";

    @Override
    public List<KnowledgeCategory> getCategoryTree() {
        List<KnowledgeCategory> all = listCategories();
        Map<Long, List<KnowledgeCategory>> byParent = all.stream()
                .collect(Collectors.groupingBy(c -> c.getParentId() == null ? 0L : c.getParentId()));
        List<KnowledgeCategory> roots = byParent.getOrDefault(0L, new ArrayList<>());
        roots.sort(Comparator.comparing(c -> c.getSortOrder() == null ? 0 : c.getSortOrder()));
        roots.forEach(root -> root.setChildren(byParent.get(root.getId())));
        return roots;
    }

    @Override
    public List<KnowledgeCategory> listCategories() {
        return lambdaQuery()
                .eq(KnowledgeCategory::getStatus, 1)
                .orderByAsc(KnowledgeCategory::getSortOrder)
                .orderByAsc(KnowledgeCategory::getId)
                .list();
    }

    @Override
    public KnowledgeCategory createCategory(String categoryName, String categoryCode,
                                            String description, Long parentId, Integer sortOrder) {
        if (!StringUtils.hasText(categoryName)) {
            throw new BizException("分类名称不能为空");
        }
        if (!StringUtils.hasText(categoryCode)) {
            categoryCode = "CAT_" + System.currentTimeMillis();
        }
        Long exists = lambdaQuery().eq(KnowledgeCategory::getCategoryCode, categoryCode).count();
        if (exists != null && exists > 0) {
            throw new BizException("分类编码已存在: " + categoryCode);
        }
        KnowledgeCategory category = new KnowledgeCategory();
        category.setParentId(parentId == null ? 0L : parentId);
        category.setCategoryName(categoryName.trim());
        category.setCategoryCode(categoryCode.trim());
        category.setDescription(description);
        category.setSortOrder(sortOrder == null ? 0 : sortOrder);
        category.setLevel(1);
        category.setPath("0");
        category.setStatus(1);
        category.setDocumentCount(0);
        Date now = new Date();
        category.setCreateTime(now);
        category.setUpdateTime(now);
        category.setCreateUser(DEFAULT_USER);
        category.setUpdateUser(DEFAULT_USER);
        save(category);
        return category;
    }

    @Override
    public boolean updateCategory(Long id, String categoryName, String description, Integer sortOrder) {
        KnowledgeCategory category = getById(id);
        if (category == null) {
            throw new BizException("分类不存在");
        }
        if (StringUtils.hasText(categoryName)) {
            category.setCategoryName(categoryName.trim());
        }
        if (description != null) {
            category.setDescription(description);
        }
        if (sortOrder != null) {
            category.setSortOrder(sortOrder);
        }
        category.setUpdateTime(new Date());
        category.setUpdateUser(DEFAULT_USER);
        return updateById(category);
    }

    @Override
    public boolean deleteCategory(Long id) {
        KnowledgeCategory category = getById(id);
        if (category == null) {
            throw new BizException("分类不存在");
        }
        long docCount = category.getDocumentCount() == null ? 0 : category.getDocumentCount();
        if (docCount > 0) {
            throw new BizException("该分类下还有 " + docCount + " 篇文档，请先移除文档后再删除分类");
        }
        return removeById(id);
    }
}
