package com.kblite.knowledge.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.kblite.knowledge.entity.KnowledgeCategory;

import java.util.List;

/**
 * 知识库分类服务
 *
 * @author kb-agent-lite
 */
public interface KnowledgeCategoryService extends IService<KnowledgeCategory> {

    /** 分类树 */
    List<KnowledgeCategory> getCategoryTree();

    /** 平铺列表（按排序） */
    List<KnowledgeCategory> listCategories();

    /** 新增分类 */
    KnowledgeCategory createCategory(String categoryName, String categoryCode,
                                     String description, Long parentId, Integer sortOrder);

    /** 更新分类 */
    boolean updateCategory(Long id, String categoryName, String description, Integer sortOrder);

    /** 删除分类（有文档时拒绝） */
    boolean deleteCategory(Long id);
}
