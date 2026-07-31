package com.myagent.assistant.paper.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.myagent.assistant.paper.dto.PaperCategoryCreateRequest;
import com.myagent.assistant.paper.entity.PaperCategory;
import com.myagent.assistant.paper.entity.PaperReference;
import com.myagent.assistant.paper.mapper.PaperCategoryMapper;
import com.myagent.assistant.paper.mapper.PaperReferenceMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaperCategoryServiceImplTest {

    @Test
    void createCategoryRejectsBlankName() {
        PaperCategoryMapper paperCategoryMapper = mock(PaperCategoryMapper.class);
        PaperReferenceMapper paperReferenceMapper = mock(PaperReferenceMapper.class);
        PaperCategoryServiceImpl service = new PaperCategoryServiceImpl(paperCategoryMapper, paperReferenceMapper);

        PaperCategoryCreateRequest request = new PaperCategoryCreateRequest();
        request.setName("  ");

        assertThatThrownBy(() -> service.createCategory(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("分类名称不能为空");

        verify(paperCategoryMapper, never()).insert(any(PaperCategory.class));
    }

    @Test
    void createCategoryRejectsDuplicateName() {
        PaperCategoryMapper paperCategoryMapper = mock(PaperCategoryMapper.class);
        PaperReferenceMapper paperReferenceMapper = mock(PaperReferenceMapper.class);
        PaperCategoryServiceImpl service = new PaperCategoryServiceImpl(paperCategoryMapper, paperReferenceMapper);

        PaperCategory existing = new PaperCategory();
        existing.setId(2L);
        existing.setName("RAG 核心论文");
        when(paperCategoryMapper.selectOne(any(Wrapper.class))).thenReturn(existing);

        PaperCategoryCreateRequest request = new PaperCategoryCreateRequest();
        request.setName("RAG 核心论文");

        assertThatThrownBy(() -> service.createCategory(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("分类名称已存在");

        verify(paperCategoryMapper, never()).insert(any(PaperCategory.class));
    }

    @Test
    void deleteCategoryMovesPapersToUncategorizedBeforeDelete() {
        PaperCategoryMapper paperCategoryMapper = mock(PaperCategoryMapper.class);
        PaperReferenceMapper paperReferenceMapper = mock(PaperReferenceMapper.class);
        PaperCategoryServiceImpl service = new PaperCategoryServiceImpl(paperCategoryMapper, paperReferenceMapper);

        PaperCategory target = new PaperCategory();
        target.setId(2L);
        target.setName("RAG 核心论文");
        target.setSystemFlag(false);

        PaperCategory uncategorized = new PaperCategory();
        uncategorized.setId(1L);
        uncategorized.setName("未分类");
        uncategorized.setSystemFlag(true);

        when(paperCategoryMapper.selectById(2L)).thenReturn(target);
        when(paperCategoryMapper.selectOne(any(Wrapper.class))).thenReturn(uncategorized);

        service.deleteCategory(2L);

        ArgumentCaptor<PaperReference> paperCaptor = ArgumentCaptor.forClass(PaperReference.class);
        verify(paperReferenceMapper).update(paperCaptor.capture(), any(UpdateWrapper.class));
        assertThat(paperCaptor.getValue().getCategoryId()).isEqualTo(1L);
        verify(paperCategoryMapper).deleteById(2L);
    }

    @Test
    void deleteCategoryRejectsSystemCategory() {
        PaperCategoryMapper paperCategoryMapper = mock(PaperCategoryMapper.class);
        PaperReferenceMapper paperReferenceMapper = mock(PaperReferenceMapper.class);
        PaperCategoryServiceImpl service = new PaperCategoryServiceImpl(paperCategoryMapper, paperReferenceMapper);

        PaperCategory systemCategory = new PaperCategory();
        systemCategory.setId(1L);
        systemCategory.setName("未分类");
        systemCategory.setSystemFlag(true);
        when(paperCategoryMapper.selectById(1L)).thenReturn(systemCategory);

        assertThatThrownBy(() -> service.deleteCategory(1L))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("系统分类不能删除");

        verify(paperReferenceMapper, never()).update(any(PaperReference.class), any(Wrapper.class));
        verify(paperCategoryMapper, never()).deleteById(1L);
    }
}
