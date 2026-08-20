package com.myagent.assistant.rag.service.impl;

import com.myagent.assistant.knowledge.entity.PaperCatalog;
import com.myagent.assistant.knowledge.entity.PaperKnowledgeUnit;
import com.myagent.assistant.knowledge.mapper.PaperCatalogMapper;
import com.myagent.assistant.knowledge.service.PaperKnowledgeService;
import com.myagent.assistant.paper.entity.PaperProfile;
import com.myagent.assistant.paper.entity.PaperReference;
import com.myagent.assistant.paper.entity.PaperSectionSummary;
import com.myagent.assistant.paper.mapper.PaperProfileMapper;
import com.myagent.assistant.paper.mapper.PaperReferenceMapper;
import com.myagent.assistant.paper.mapper.PaperSectionSummaryMapper;
import com.myagent.assistant.rag.context.StructuredEvidenceContext;
import com.myagent.assistant.rag.dto.RagSource;
import com.myagent.assistant.rag.service.RagRetrievalService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StructuredEvidenceServiceImplTest {

    @Test
    void exactSingleQuestionUsesKnowledgeWithoutCallingRag() {
        Fixture fixture = new Fixture();
        PaperKnowledgeUnit unit = knowledgeUnit(7L, "DATASET", "SCADA dataset");
        when(fixture.knowledgeService.findUnits(eq(List.of(7L)), any(), anyInt())).thenReturn(List.of(unit));

        StructuredEvidenceContext context = fixture.service.build("这篇论文使用了什么数据集？", List.of(7L), 5);

        assertTrue(context.isHandled());
        assertEquals("STRUCTURED_FIRST_SINGLE", context.getContextStrategy());
        assertFalse(context.getPlan().getRagUsed());
        assertEquals("knowledge_unit", context.getSources().get(0).getSourceType());
        verify(fixture.retrievalService, never()).retrieveSources(any(), anyInt(), any());
    }

    @Test
    void sectionSummaryCoversExactQuestionWhenKnowledgeIndexIsMissing() {
        Fixture fixture = new Fixture();
        when(fixture.knowledgeService.findUnits(any(), any(), anyInt())).thenReturn(List.of());
        PaperSectionSummary summary = new PaperSectionSummary();
        summary.setId(31L);
        summary.setPaperId(7L);
        summary.setSectionId(20L);
        summary.setSectionType("EXPERIMENT");
        summary.setSectionTitle("Experiments");
        summary.setSummary("The experiment uses SCADA data.");
        summary.setSummaryVersion("section-summary-v1");
        when(fixture.summaryMapper.selectList(any())).thenReturn(List.of(summary));

        StructuredEvidenceContext context = fixture.service.build("这篇论文使用了什么数据集？", List.of(7L), 5);

        assertTrue(context.isHandled());
        assertFalse(context.getPlan().getRagUsed());
        assertEquals("section_summary", context.getSources().get(0).getSourceType());
    }

    @Test
    void fuzzyQuestionAddsRagAsSupplement() {
        Fixture fixture = new Fixture();
        PaperProfile profile = new PaperProfile();
        profile.setId(9L);
        profile.setPaperId(7L);
        profile.setProfileVersion("paper-profile-v1");
        profile.setProfileText("This paper models spatial dependence.");
        when(fixture.profileMapper.selectList(any())).thenReturn(List.of(profile));
        when(fixture.knowledgeService.findUnits(any(), any(), anyInt())).thenReturn(List.of());
        RagSource rag = new RagSource();
        rag.setPaperId(7L);
        rag.setChunkId(99L);
        rag.setContent("Graph-based spatial evidence");
        when(fixture.retrievalService.retrieveSources(any(), anyInt(), any())).thenReturn(List.of(rag));

        StructuredEvidenceContext context = fixture.service.build("有哪些比较新颖的空间建模方向？", List.of(7L), 5);

        assertTrue(context.isHandled());
        assertTrue(context.getPlan().getRagUsed());
        assertEquals(1, context.getPlan().getRagSourceCount());
    }

    @Test
    void libraryDiscoveryRemovesQuestionWordsBeforeMatchingCatalog() {
        Fixture fixture = new Fixture();
        PaperCatalog catalog = new PaperCatalog();
        catalog.setPaperId(7L);
        catalog.setStatus("COMPLETED");
        catalog.setCatalogText("风速预测方法：NARX neural network");
        when(fixture.catalogMapper.selectList(any())).thenReturn(List.of(catalog));
        when(fixture.knowledgeService.findUnits(eq(List.of(7L)), any(), anyInt()))
                .thenReturn(List.of(knowledgeUnit(7L, "METHOD", "NARX neural network")));

        StructuredEvidenceContext context = fixture.service.build("风速预测有哪些方法？", List.of(), 5);

        assertTrue(context.isHandled());
        assertEquals(List.of(7L), context.getPlan().getCandidatePaperIds());
        assertFalse(context.getPlan().getRagUsed());
    }

    private static PaperKnowledgeUnit knowledgeUnit(Long paperId, String type, String value) {
        PaperKnowledgeUnit unit = new PaperKnowledgeUnit();
        unit.setId(1L);
        unit.setPaperId(paperId);
        unit.setKnowledgeType(type);
        unit.setSubjectText("Paper");
        unit.setPredicateText("uses");
        unit.setObjectValue(value);
        unit.setEvidenceText(value);
        unit.setConfidenceLevel("SILVER");
        return unit;
    }

    private static class Fixture {
        final PaperKnowledgeService knowledgeService = mock(PaperKnowledgeService.class);
        final PaperCatalogMapper catalogMapper = mock(PaperCatalogMapper.class);
        final PaperReferenceMapper paperMapper = mock(PaperReferenceMapper.class);
        final PaperProfileMapper profileMapper = mock(PaperProfileMapper.class);
        final PaperSectionSummaryMapper summaryMapper = mock(PaperSectionSummaryMapper.class);
        final RagRetrievalService retrievalService = mock(RagRetrievalService.class);
        final StructuredEvidenceServiceImpl service;

        Fixture() {
            PaperReference paper = new PaperReference();
            paper.setId(7L);
            paper.setTitle("Wind forecasting paper");
            when(paperMapper.selectBatchIds(any())).thenReturn(List.of(paper));
            when(profileMapper.selectList(any())).thenReturn(List.of());
            when(summaryMapper.selectList(any())).thenReturn(List.of());
            when(catalogMapper.selectList(any())).thenReturn(List.<PaperCatalog>of());
            service = new StructuredEvidenceServiceImpl(
                    new EvidenceQueryPlannerImpl(), knowledgeService, catalogMapper, paperMapper,
                    profileMapper, summaryMapper, retrievalService, true);
        }
    }
}
