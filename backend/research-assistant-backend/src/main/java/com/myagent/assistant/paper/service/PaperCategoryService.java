package com.myagent.assistant.paper.service;

import com.myagent.assistant.paper.dto.PaperCategoryCreateRequest;
import com.myagent.assistant.paper.dto.PaperCategoryResponse;
import com.myagent.assistant.paper.dto.PaperCategoryUpdateRequest;
import com.myagent.assistant.paper.entity.PaperCategory;

import java.util.List;

public interface PaperCategoryService {

    List<PaperCategoryResponse> listCategories();

    PaperCategoryResponse createCategory(PaperCategoryCreateRequest request);

    PaperCategoryResponse updateCategory(Long id, PaperCategoryUpdateRequest request);

    void deleteCategory(Long id);

    Long getUncategorizedCategoryId();

    PaperCategory ensureCategoryExists(Long categoryId);
}
