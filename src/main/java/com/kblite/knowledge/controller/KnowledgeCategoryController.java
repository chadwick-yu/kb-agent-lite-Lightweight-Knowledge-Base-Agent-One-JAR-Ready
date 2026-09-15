package com.kblite.knowledge.controller;

import com.kblite.common.ApiResult;
import com.kblite.knowledge.entity.KnowledgeCategory;
import com.kblite.knowledge.service.KnowledgeCategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 知识库分类管理接口
 *
 * @author kb-agent-lite
 */
@RestController
@RequestMapping("/api/knowledge/category")
@RequiredArgsConstructor
public class KnowledgeCategoryController {

    private final KnowledgeCategoryService knowledgeCategoryService;

    /** 分类树 */
    @GetMapping("/tree")
    public ApiResult<List<KnowledgeCategory>> tree() {
        return ApiResult.ok(knowledgeCategoryService.getCategoryTree());
    }

    /** 平铺列表 */
    @GetMapping("/list")
    public ApiResult<List<KnowledgeCategory>> list() {
        return ApiResult.ok(knowledgeCategoryService.listCategories());
    }

    /** 新增分类，body: {categoryName, categoryCode?, description?, parentId?, sortOrder?} */
    @PostMapping
    public ApiResult<KnowledgeCategory> create(@RequestBody Map<String, Object> body) {
        String categoryName = (String) body.get("categoryName");
        String categoryCode = (String) body.get("categoryCode");
        String description = (String) body.get("description");
        Long parentId = body.get("parentId") == null ? null : Long.valueOf(String.valueOf(body.get("parentId")));
        Integer sortOrder = body.get("sortOrder") == null ? null : Integer.valueOf(String.valueOf(body.get("sortOrder")));
        return ApiResult.ok(knowledgeCategoryService.createCategory(
                categoryName, categoryCode, description, parentId, sortOrder));
    }

    /** 更新分类，body: {categoryName?, description?, sortOrder?} */
    @PutMapping("/{id}")
    public ApiResult<Boolean> update(@PathVariable("id") Long id, @RequestBody Map<String, Object> body) {
        String categoryName = (String) body.get("categoryName");
        String description = (String) body.get("description");
        Integer sortOrder = body.get("sortOrder") == null ? null : Integer.valueOf(String.valueOf(body.get("sortOrder")));
        return ApiResult.ok(knowledgeCategoryService.updateCategory(id, categoryName, description, sortOrder));
    }

    /** 删除分类（分类下有文档时拒绝） */
    @DeleteMapping("/{id}")
    public ApiResult<Boolean> delete(@PathVariable("id") Long id) {
        return ApiResult.ok(knowledgeCategoryService.deleteCategory(id));
    }
}
