package com.myagent.assistant.paper.service.impl;

import com.myagent.assistant.paper.entity.PaperCategory;
import com.myagent.assistant.paper.entity.PaperChunk;
import com.myagent.assistant.paper.entity.PaperReference;
import com.myagent.assistant.paper.entity.PaperSection;
import com.myagent.assistant.paper.dto.PaperMetadataUpdateRequest;
import com.myagent.assistant.paper.mapper.PaperChunkMapper;
import com.myagent.assistant.paper.mapper.PaperProfileJobMapper;
import com.myagent.assistant.paper.mapper.PaperProfileMapper;
import com.myagent.assistant.paper.mapper.PaperReferenceMapper;
import com.myagent.assistant.paper.mapper.PaperSectionMapper;
import com.myagent.assistant.paper.mapper.PaperSectionSummaryMapper;
import com.myagent.assistant.paper.service.PaperCategoryService;
import com.myagent.assistant.paper.service.PdfParseService;
import com.myagent.assistant.paper.service.PaperAssetService;
import com.myagent.assistant.paper.structure.SectionType;
import com.myagent.assistant.paper.structure.StructuredChunk;
import com.myagent.assistant.paper.structure.StructuredChunkingService;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.myagent.assistant.qdrant.service.QdrantService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaperReferenceServiceImplTest {

    @TempDir
    Path tempDir;

    @Test
    void uploadPaperUsesUncategorizedWhenCategoryIdMissing() throws Exception {
        PaperReferenceMapper paperReferenceMapper = mock(PaperReferenceMapper.class);
        PaperCategoryService paperCategoryService = mock(PaperCategoryService.class);
        PaperReferenceServiceImpl service = createService(paperReferenceMapper, paperCategoryService);

        PaperCategory uncategorized = new PaperCategory();
        uncategorized.setId(1L);
        uncategorized.setName("未分类");
        when(paperCategoryService.ensureCategoryExists(null)).thenReturn(uncategorized);

        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn("paper.pdf");
        when(file.getSize()).thenReturn(1024L);

        PaperReference result = service.uploadPaper(file, "论文标题", null, null, null, null, null, null);

        ArgumentCaptor<PaperReference> paperCaptor = ArgumentCaptor.forClass(PaperReference.class);
        verify(paperReferenceMapper).insert(paperCaptor.capture());
        assertThat(paperCaptor.getValue().getCategoryId()).isEqualTo(1L);
        assertThat(result.getCategoryName()).isEqualTo("未分类");
    }

    @Test
    void uploadPaperValidatesProvidedCategory() throws Exception {
        PaperReferenceMapper paperReferenceMapper = mock(PaperReferenceMapper.class);
        PaperCategoryService paperCategoryService = mock(PaperCategoryService.class);
        PaperReferenceServiceImpl service = createService(paperReferenceMapper, paperCategoryService);

        PaperCategory category = new PaperCategory();
        category.setId(2L);
        category.setName("RAG 核心论文");
        when(paperCategoryService.ensureCategoryExists(2L)).thenReturn(category);

        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn("rag.pdf");
        when(file.getSize()).thenReturn(2048L);

        PaperReference result = service.uploadPaper(file, "RAG", null, null, null, null, null, 2L);

        verify(paperCategoryService).ensureCategoryExists(2L);
        assertThat(result.getCategoryId()).isEqualTo(2L);
        assertThat(result.getCategoryName()).isEqualTo("RAG 核心论文");
    }

    @Test
    void updatePaperCategoryRejectsMissingPaper() {
        PaperReferenceMapper paperReferenceMapper = mock(PaperReferenceMapper.class);
        PaperCategoryService paperCategoryService = mock(PaperCategoryService.class);
        PaperReferenceServiceImpl service = createService(paperReferenceMapper, paperCategoryService);

        when(paperReferenceMapper.selectById(99L)).thenReturn(null);

        assertThatThrownBy(() -> service.updatePaperCategory(99L, 2L))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("文献不存在");

        verify(paperCategoryService, never()).ensureCategoryExists(any());
        verify(paperReferenceMapper, never()).updateById(any(PaperReference.class));
    }

    @Test
    void updatePaperCategoryUpdatesCategoryOnly() {
        PaperReferenceMapper paperReferenceMapper = mock(PaperReferenceMapper.class);
        PaperCategoryService paperCategoryService = mock(PaperCategoryService.class);
        PaperReferenceServiceImpl service = createService(paperReferenceMapper, paperCategoryService);

        PaperReference paper = new PaperReference();
        paper.setId(3L);
        paper.setTitle("原论文");
        paper.setParseStatus("COMPLETED");
        paper.setVectorStatus("COMPLETED");
        when(paperReferenceMapper.selectById(3L)).thenReturn(paper);

        PaperCategory category = new PaperCategory();
        category.setId(2L);
        category.setName("RAG 核心论文");
        when(paperCategoryService.ensureCategoryExists(2L)).thenReturn(category);

        PaperReference result = service.updatePaperCategory(3L, 2L);

        assertThat(result.getCategoryId()).isEqualTo(2L);
        assertThat(result.getCategoryName()).isEqualTo("RAG 核心论文");
        assertThat(result.getParseStatus()).isEqualTo("COMPLETED");
        assertThat(result.getVectorStatus()).isEqualTo("COMPLETED");
        ArgumentCaptor<PaperReference> updateCaptor = ArgumentCaptor.forClass(PaperReference.class);
        verify(paperReferenceMapper).update(updateCaptor.capture(), any(Wrapper.class));
        assertThat(updateCaptor.getValue().getCategoryId()).isEqualTo(2L);
        assertThat(updateCaptor.getValue().getTitle()).isNull();
    }

    @Test
    void updatePaperMetadataChangesEditableFieldsAndKeepsProcessingState() {
        PaperReferenceMapper paperReferenceMapper = mock(PaperReferenceMapper.class);
        PaperCategoryService paperCategoryService = mock(PaperCategoryService.class);
        PaperReferenceServiceImpl service = createService(paperReferenceMapper, paperCategoryService);

        PaperReference paper = new PaperReference();
        paper.setId(8L);
        paper.setCategoryId(2L);
        paper.setTitle("旧标题");
        paper.setParseStatus("COMPLETED");
        paper.setVectorStatus("COMPLETED");
        when(paperReferenceMapper.selectById(8L)).thenReturn(paper);

        PaperCategory category = new PaperCategory();
        category.setId(2L);
        category.setName("风速预测");
        when(paperCategoryService.ensureCategoryExists(2L)).thenReturn(category);

        PaperMetadataUpdateRequest request = new PaperMetadataUpdateRequest();
        request.setTitle("  新标题  ");
        request.setAuthors(" Zhang, Li ");
        request.setPublishYear(2025);
        request.setJournal("Energy AI");
        request.setKeywords("wind, forecasting");
        request.setAbstractText("摘要内容");
        request.setRemark("重点阅读");

        PaperReference result = service.updatePaperMetadata(8L, request);

        assertThat(result.getTitle()).isEqualTo("新标题");
        assertThat(result.getAuthors()).isEqualTo("Zhang, Li");
        assertThat(result.getPublishYear()).isEqualTo(2025);
        assertThat(result.getCategoryName()).isEqualTo("风速预测");
        assertThat(result.getParseStatus()).isEqualTo("COMPLETED");
        assertThat(result.getVectorStatus()).isEqualTo("COMPLETED");
        verify(paperReferenceMapper).update(eq(null), any(Wrapper.class));
        verify(paperReferenceMapper, never()).updateById(any(PaperReference.class));
    }

    @Test
    void updatePaperMetadataRejectsBlankTitleAndInvalidYear() {
        PaperReferenceMapper paperReferenceMapper = mock(PaperReferenceMapper.class);
        PaperReferenceServiceImpl service = createService(paperReferenceMapper, mock(PaperCategoryService.class));

        PaperReference paper = new PaperReference();
        paper.setId(8L);
        when(paperReferenceMapper.selectById(8L)).thenReturn(paper);

        PaperMetadataUpdateRequest blankTitle = new PaperMetadataUpdateRequest();
        blankTitle.setTitle("  ");
        assertThatThrownBy(() -> service.updatePaperMetadata(8L, blankTitle))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("文献标题不能为空");

        PaperMetadataUpdateRequest invalidYear = new PaperMetadataUpdateRequest();
        invalidYear.setTitle("有效标题");
        invalidYear.setPublishYear(999);
        assertThatThrownBy(() -> service.updatePaperMetadata(8L, invalidYear))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("发表年份必须在1000到2100之间");

        verify(paperReferenceMapper, never()).update(any(), any(Wrapper.class));
    }

    @Test
    void parsePaperWritesStructuredSectionsAndChunks() {
        PaperReferenceMapper paperReferenceMapper = mock(PaperReferenceMapper.class);
        PaperChunkMapper paperChunkMapper = mock(PaperChunkMapper.class);
        PaperSectionMapper paperSectionMapper = mock(PaperSectionMapper.class);
        PdfParseService pdfParseService = mock(PdfParseService.class);
        QdrantService qdrantService = mock(QdrantService.class);
        PaperCategoryService paperCategoryService = mock(PaperCategoryService.class);
        StructuredChunkingService structuredChunkingService = mock(StructuredChunkingService.class);

        PaperReference paper = new PaperReference();
        paper.setId(10L);
        paper.setTitle("Wind Paper");
        paper.setFilePath(tempDir.resolve("paper.pdf").toString());
        when(paperReferenceMapper.selectById(10L)).thenReturn(paper);
        when(pdfParseService.parseText(paper.getFilePath())).thenReturn("Method\ncontent");

        StructuredChunk structuredChunk = new StructuredChunk(
                "Method",
                SectionType.METHOD,
                0,
                "method content",
                "Section Type: METHOD\nContent:\nmethod content",
                0,
                4,
                14,
                false,
                false,
                1.0,
                "paper-structure-v1"
        );
        when(structuredChunkingService.chunk("Wind Paper", "Method\ncontent"))
                .thenReturn(List.of(structuredChunk));

        PaperReferenceServiceImpl service = new PaperReferenceServiceImpl(
                paperReferenceMapper,
                paperChunkMapper,
                pdfParseService,
                qdrantService,
                paperCategoryService,
                paperSectionMapper,
                structuredChunkingService,
                mock(PaperSectionSummaryMapper.class),
                mock(PaperProfileMapper.class),
                mock(PaperProfileJobMapper.class),
                mock(PaperAssetService.class)
        );
        ReflectionTestUtils.setField(service, "uploadDir", tempDir.toString());

        int count = service.parsePaper(10L);

        assertThat(count).isEqualTo(1);
        verify(paperSectionMapper).insert(any(PaperSection.class));
        verify(paperChunkMapper).insert(any(PaperChunk.class));
    }

    @Test
    void deletePaperDeletesQdrantPointsDerivedRowsReferenceAndLocalFile() throws Exception {
        PaperReferenceMapper paperReferenceMapper = mock(PaperReferenceMapper.class);
        PaperChunkMapper paperChunkMapper = mock(PaperChunkMapper.class);
        PaperSectionMapper paperSectionMapper = mock(PaperSectionMapper.class);
        PaperSectionSummaryMapper paperSectionSummaryMapper = mock(PaperSectionSummaryMapper.class);
        PaperProfileMapper paperProfileMapper = mock(PaperProfileMapper.class);
        PaperProfileJobMapper paperProfileJobMapper = mock(PaperProfileJobMapper.class);
        PaperAssetService paperAssetService = mock(PaperAssetService.class);
        QdrantService qdrantService = mock(QdrantService.class);

        Path file = tempDir.resolve("delete-me.pdf");
        Files.writeString(file, "pdf");

        PaperReference paper = new PaperReference();
        paper.setId(12L);
        paper.setFilePath(file.toString());
        when(paperReferenceMapper.selectById(12L)).thenReturn(paper);
        when(qdrantService.deletePaperPoints(12L)).thenReturn(Map.of("success", true));

        PaperReferenceServiceImpl service = createService(
                paperReferenceMapper,
                mock(PaperCategoryService.class),
                paperChunkMapper,
                paperSectionMapper,
                qdrantService,
                paperSectionSummaryMapper,
                paperProfileMapper,
                paperProfileJobMapper,
                paperAssetService
        );

        service.deletePaper(12L);

        verify(qdrantService).deletePaperPoints(12L);
        verify(paperAssetService).deleteForPaper(12L);
        verify(paperProfileJobMapper).delete(any(Wrapper.class));
        verify(paperProfileMapper).delete(any(Wrapper.class));
        verify(paperSectionSummaryMapper).delete(any(Wrapper.class));
        verify(paperChunkMapper).delete(any(Wrapper.class));
        verify(paperSectionMapper).delete(any(Wrapper.class));
        verify(paperReferenceMapper).deleteById(12L);
        assertThat(Files.exists(file)).isFalse();
    }

    @Test
    void deletePaperStopsWhenQdrantDeleteFails() {
        PaperReferenceMapper paperReferenceMapper = mock(PaperReferenceMapper.class);
        PaperChunkMapper paperChunkMapper = mock(PaperChunkMapper.class);
        PaperSectionMapper paperSectionMapper = mock(PaperSectionMapper.class);
        PaperSectionSummaryMapper paperSectionSummaryMapper = mock(PaperSectionSummaryMapper.class);
        PaperProfileMapper paperProfileMapper = mock(PaperProfileMapper.class);
        PaperProfileJobMapper paperProfileJobMapper = mock(PaperProfileJobMapper.class);
        QdrantService qdrantService = mock(QdrantService.class);

        PaperReference paper = new PaperReference();
        paper.setId(13L);
        paper.setFilePath(tempDir.resolve("missing.pdf").toString());
        when(paperReferenceMapper.selectById(13L)).thenReturn(paper);
        when(qdrantService.deletePaperPoints(13L)).thenReturn(Map.of("success", false, "error", "Qdrant unavailable"));

        PaperReferenceServiceImpl service = createService(
                paperReferenceMapper,
                mock(PaperCategoryService.class),
                paperChunkMapper,
                paperSectionMapper,
                qdrantService,
                paperSectionSummaryMapper,
                paperProfileMapper,
                paperProfileJobMapper
        );

        assertThatThrownBy(() -> service.deletePaper(13L))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("删除 Qdrant 向量失败：Qdrant unavailable");

        verify(paperReferenceMapper, never()).deleteById(any(Long.class));
        verify(paperChunkMapper, never()).delete(any(Wrapper.class));
        verify(paperSectionMapper, never()).delete(any(Wrapper.class));
        verify(paperSectionSummaryMapper, never()).delete(any(Wrapper.class));
        verify(paperProfileMapper, never()).delete(any(Wrapper.class));
        verify(paperProfileJobMapper, never()).delete(any(Wrapper.class));
    }

    @Test
    void reprocessPaperAssetsCleansDerivedAssetsAndKeepsPaperReference() {
        PaperReferenceMapper paperReferenceMapper = mock(PaperReferenceMapper.class);
        PaperChunkMapper paperChunkMapper = mock(PaperChunkMapper.class);
        PdfParseService pdfParseService = mock(PdfParseService.class);
        QdrantService qdrantService = mock(QdrantService.class);
        PaperCategoryService categoryService = mock(PaperCategoryService.class);
        PaperSectionMapper paperSectionMapper = mock(PaperSectionMapper.class);
        PaperSectionSummaryMapper sectionSummaryMapper = mock(PaperSectionSummaryMapper.class);
        PaperProfileMapper profileMapper = mock(PaperProfileMapper.class);
        PaperProfileJobMapper profileJobMapper = mock(PaperProfileJobMapper.class);
        PaperAssetService paperAssetService = mock(PaperAssetService.class);

        PaperReference paper = new PaperReference();
        paper.setId(7L);
        paper.setTitle("Wind Paper");
        paper.setFilePath("paper.pdf");
        when(paperReferenceMapper.selectById(7L)).thenReturn(paper);
        when(qdrantService.deletePaperPoints(7L)).thenReturn(Map.of("success", true));
        when(qdrantService.upsertPaperChunks(eq(7L), any())).thenReturn(Map.of("success", true));
        when(pdfParseService.parseText("paper.pdf")).thenReturn("Method\nThis paper proposes a model for wind prediction.");
        when(paperChunkMapper.selectList(any(Wrapper.class))).thenAnswer(invocation -> List.of(chunk(1L, 7L)));

        PaperReferenceServiceImpl service = new PaperReferenceServiceImpl(
                paperReferenceMapper,
                paperChunkMapper,
                pdfParseService,
                qdrantService,
                categoryService,
                paperSectionMapper,
                new StructuredChunkingService(new com.myagent.assistant.paper.structure.PaperTextCleaner(), new com.myagent.assistant.paper.structure.PaperSectionDetector()),
                sectionSummaryMapper,
                profileMapper,
                profileJobMapper,
                paperAssetService
        );

        Map<String, Object> result = service.reprocessPaperAssets(7L);

        assertThat(result.get("success")).isEqualTo(true);
        verify(qdrantService).deletePaperPoints(7L);
        verify(paperAssetService).deleteForPaper(7L);
        verify(profileJobMapper).delete(any(Wrapper.class));
        verify(profileMapper).delete(any(Wrapper.class));
        verify(sectionSummaryMapper).delete(any(Wrapper.class));
        verify(paperChunkMapper, atLeastOnce()).delete(any(Wrapper.class));
        verify(paperSectionMapper, atLeastOnce()).delete(any(Wrapper.class));
        verify(paperReferenceMapper, never()).deleteById(7L);
    }

    private PaperChunk chunk(Long id, Long paperId) {
        PaperChunk chunk = new PaperChunk();
        chunk.setId(id);
        chunk.setPaperId(paperId);
        chunk.setContent("method content");
        return chunk;
    }

    private PaperReferenceServiceImpl createService(PaperReferenceMapper paperReferenceMapper,
                                                    PaperCategoryService paperCategoryService) {
        return createService(
                paperReferenceMapper,
                paperCategoryService,
                mock(PaperChunkMapper.class),
                mock(PaperSectionMapper.class),
                mock(QdrantService.class),
                mock(PaperSectionSummaryMapper.class),
                mock(PaperProfileMapper.class),
                mock(PaperProfileJobMapper.class)
        );
    }

    private PaperReferenceServiceImpl createService(PaperReferenceMapper paperReferenceMapper,
                                                    PaperCategoryService paperCategoryService,
                                                    PaperChunkMapper paperChunkMapper,
                                                    PaperSectionMapper paperSectionMapper,
                                                    QdrantService qdrantService,
                                                    PaperSectionSummaryMapper paperSectionSummaryMapper,
                                                    PaperProfileMapper paperProfileMapper,
                                                    PaperProfileJobMapper paperProfileJobMapper) {
        return createService(paperReferenceMapper, paperCategoryService, paperChunkMapper, paperSectionMapper,
                qdrantService, paperSectionSummaryMapper, paperProfileMapper, paperProfileJobMapper,
                mock(PaperAssetService.class));
    }

    private PaperReferenceServiceImpl createService(PaperReferenceMapper paperReferenceMapper,
                                                    PaperCategoryService paperCategoryService,
                                                    PaperChunkMapper paperChunkMapper,
                                                    PaperSectionMapper paperSectionMapper,
                                                    QdrantService qdrantService,
                                                    PaperSectionSummaryMapper paperSectionSummaryMapper,
                                                    PaperProfileMapper paperProfileMapper,
                                                    PaperProfileJobMapper paperProfileJobMapper,
                                                    PaperAssetService paperAssetService) {
        PaperReferenceServiceImpl service = new PaperReferenceServiceImpl(
                paperReferenceMapper,
                paperChunkMapper,
                mock(PdfParseService.class),
                qdrantService,
                paperCategoryService,
                paperSectionMapper,
                mock(StructuredChunkingService.class),
                paperSectionSummaryMapper,
                paperProfileMapper,
                paperProfileJobMapper,
                paperAssetService
        );
        ReflectionTestUtils.setField(service, "uploadDir", tempDir.toString());
        return service;
    }
}
