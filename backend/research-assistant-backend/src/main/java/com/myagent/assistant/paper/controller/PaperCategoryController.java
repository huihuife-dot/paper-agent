package com.myagent.assistant.paper.controller;

import com.myagent.assistant.paper.common.Result;
import com.myagent.assistant.paper.dto.PaperCategoryCreateRequest;
import com.myagent.assistant.paper.dto.PaperCategoryResponse;
import com.myagent.assistant.paper.dto.PaperCategoryUpdateRequest;
import com.myagent.assistant.paper.service.PaperCategoryService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class PaperCategoryController {

    private final PaperCategoryService paperCategoryService;

    public PaperCategoryController(PaperCategoryService paperCategoryService) {
        this.paperCategoryService = paperCategoryService;
    }

    /**
     * 查询文献分类文件夹列表。
     *
     * 访问示例：
     * GET /api/paper-categories
     */
    @GetMapping("/api/paper-categories")
    public Result<List<PaperCategoryResponse>> list() {
        return Result.success(paperCategoryService.listCategories());
    }

    /**
     * 新建文献分类文件夹。
     *
     * 访问示例：
     * POST /api/paper-categories
     */
    @PostMapping("/api/paper-categories")
    public Result<PaperCategoryResponse> create(@RequestBody PaperCategoryCreateRequest request) {
        return Result.success(paperCategoryService.createCategory(request));
    }

    /**
     * 修改文献分类文件夹。
     *
     * 访问示例：
     * PUT /api/paper-categories/2
     */
    @PutMapping("/api/paper-categories/{id}")
    public Result<PaperCategoryResponse> update(@PathVariable Long id,
                                                @RequestBody PaperCategoryUpdateRequest request) {
        return Result.success(paperCategoryService.updateCategory(id, request));
    }

    /**
     * 删除文献分类文件夹。
     *
     * 删除普通分类时，不删除文献，而是把文献移动到“未分类”。
     */
    @DeleteMapping("/api/paper-categories/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        paperCategoryService.deleteCategory(id);
        return Result.success();
    }
}
