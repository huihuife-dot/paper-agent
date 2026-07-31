package com.myagent.assistant.paper.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.myagent.assistant.paper.dto.PaperCategoryCreateRequest;
import com.myagent.assistant.paper.dto.PaperCategoryResponse;
import com.myagent.assistant.paper.dto.PaperCategoryUpdateRequest;
import com.myagent.assistant.paper.entity.PaperCategory;
import com.myagent.assistant.paper.entity.PaperReference;
import com.myagent.assistant.paper.mapper.PaperCategoryMapper;
import com.myagent.assistant.paper.mapper.PaperReferenceMapper;
import com.myagent.assistant.paper.service.PaperCategoryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class PaperCategoryServiceImpl implements PaperCategoryService {

    private static final String UNCATEGORIZED_NAME = "未分类";

    private final PaperCategoryMapper paperCategoryMapper;
    private final PaperReferenceMapper paperReferenceMapper;

    public PaperCategoryServiceImpl(PaperCategoryMapper paperCategoryMapper,
                                    PaperReferenceMapper paperReferenceMapper) {
        this.paperCategoryMapper = paperCategoryMapper;
        this.paperReferenceMapper = paperReferenceMapper;
    }

    @Override
    public List<PaperCategoryResponse> listCategories() {
        List<PaperCategory> categories = paperCategoryMapper.selectList(
                new QueryWrapper<PaperCategory>()
                        .orderByAsc("sort_order")
                        .orderByAsc("id")
        );

        return categories.stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public PaperCategoryResponse createCategory(PaperCategoryCreateRequest request) {
        String name = normalizeName(request != null ? request.getName() : null);
        if (name == null) {
            throw new RuntimeException("分类名称不能为空");
        }

        if (findByName(name) != null) {
            throw new RuntimeException("分类名称已存在");
        }

        PaperCategory category = new PaperCategory();
        category.setName(name);
        category.setDescription(normalizeDescription(request.getDescription()));
        category.setSortOrder(nextSortOrder());
        category.setSystemFlag(false);
        category.setCreateTime(LocalDateTime.now());
        category.setUpdateTime(LocalDateTime.now());

        paperCategoryMapper.insert(category);
        return toResponse(category);
    }

    @Override
    public PaperCategoryResponse updateCategory(Long id, PaperCategoryUpdateRequest request) {
        PaperCategory category = paperCategoryMapper.selectById(id);
        if (category == null) {
            throw new RuntimeException("分类不存在");
        }

        if (Boolean.TRUE.equals(category.getSystemFlag())) {
            throw new RuntimeException("系统分类不能修改");
        }

        String name = normalizeName(request != null ? request.getName() : null);
        if (name == null) {
            throw new RuntimeException("分类名称不能为空");
        }

        PaperCategory sameNameCategory = findByName(name);
        if (sameNameCategory != null && !sameNameCategory.getId().equals(id)) {
            throw new RuntimeException("分类名称已存在");
        }

        category.setName(name);
        category.setDescription(normalizeDescription(request.getDescription()));
        category.setUpdateTime(LocalDateTime.now());

        paperCategoryMapper.updateById(category);
        return toResponse(category);
    }

    @Override
    @Transactional
    public void deleteCategory(Long id) {
        PaperCategory category = paperCategoryMapper.selectById(id);
        if (category == null) {
            throw new RuntimeException("分类不存在");
        }

        if (Boolean.TRUE.equals(category.getSystemFlag())) {
            throw new RuntimeException("系统分类不能删除");
        }

        Long uncategorizedId = getUncategorizedCategoryId();
        PaperReference update = new PaperReference();
        update.setCategoryId(uncategorizedId);
        paperReferenceMapper.update(
                update,
                new UpdateWrapper<PaperReference>().eq("category_id", id)
        );

        paperCategoryMapper.deleteById(id);
    }

    @Override
    public Long getUncategorizedCategoryId() {
        PaperCategory category = paperCategoryMapper.selectOne(
                new QueryWrapper<PaperCategory>()
                        .eq("name", UNCATEGORIZED_NAME)
                        .eq("system_flag", true)
                        .last("LIMIT 1")
        );

        if (category == null) {
            throw new RuntimeException("系统默认分类不存在，请先初始化 paper_category 表");
        }

        return category.getId();
    }

    @Override
    public PaperCategory ensureCategoryExists(Long categoryId) {
        if (categoryId == null) {
            return paperCategoryMapper.selectById(getUncategorizedCategoryId());
        }

        PaperCategory category = paperCategoryMapper.selectById(categoryId);
        if (category == null) {
            throw new RuntimeException("分类不存在");
        }

        return category;
    }

    private PaperCategoryResponse toResponse(PaperCategory category) {
        PaperCategoryResponse response = new PaperCategoryResponse();
        response.setId(category.getId());
        response.setName(category.getName());
        response.setDescription(category.getDescription());
        response.setSortOrder(category.getSortOrder());
        response.setSystemFlag(category.getSystemFlag());
        response.setPaperCount(countPapers(category.getId()));
        response.setCreateTime(category.getCreateTime());
        response.setUpdateTime(category.getUpdateTime());
        return response;
    }

    private Long countPapers(Long categoryId) {
        return paperReferenceMapper.selectCount(
                new QueryWrapper<PaperReference>().eq("category_id", categoryId)
        );
    }

    private PaperCategory findByName(String name) {
        if (name == null) {
            return null;
        }

        return paperCategoryMapper.selectOne(
                new QueryWrapper<PaperCategory>()
                        .eq("name", name)
                        .last("LIMIT 1")
        );
    }

    private Integer nextSortOrder() {
        PaperCategory lastCategory = paperCategoryMapper.selectOne(
                new QueryWrapper<PaperCategory>()
                        .orderByDesc("sort_order")
                        .orderByDesc("id")
                        .last("LIMIT 1")
        );

        if (lastCategory == null || lastCategory.getSortOrder() == null) {
            return 10;
        }

        return lastCategory.getSortOrder() + 10;
    }

    private String normalizeName(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }

    private String normalizeDescription(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }
}
