package com.myagent.assistant.knowledge.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myagent.assistant.knowledge.entity.PaperCatalog;
import com.myagent.assistant.knowledge.entity.PaperKnowledgeUnit;
import com.myagent.assistant.knowledge.mapper.PaperCatalogMapper;
import com.myagent.assistant.knowledge.mapper.PaperKnowledgeFeedbackMapper;
import com.myagent.assistant.knowledge.mapper.PaperKnowledgeUnitMapper;
import com.myagent.assistant.knowledge.service.KnowledgeBuildAsyncExecutor;
import com.myagent.assistant.llm.LlmService;
import com.myagent.assistant.paper.entity.PaperChunk;
import com.myagent.assistant.paper.entity.PaperProfile;
import com.myagent.assistant.paper.entity.PaperReference;
import com.myagent.assistant.paper.entity.PaperSection;
import com.myagent.assistant.paper.entity.PaperSectionSummary;
import com.myagent.assistant.paper.mapper.PaperChunkMapper;
import com.myagent.assistant.paper.mapper.PaperProfileMapper;
import com.myagent.assistant.paper.mapper.PaperReferenceMapper;
import com.myagent.assistant.paper.mapper.PaperSectionMapper;
import com.myagent.assistant.paper.mapper.PaperSectionSummaryMapper;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PaperKnowledgeServiceImplTest {

    @Test
    void buildsDeterministicAndLlmKnowledgeWithTraceableEvidence() {
        PaperCatalogMapper catalogMapper = mock(PaperCatalogMapper.class);
        PaperKnowledgeUnitMapper unitMapper = mock(PaperKnowledgeUnitMapper.class);
        PaperKnowledgeFeedbackMapper feedbackMapper = mock(PaperKnowledgeFeedbackMapper.class);
        PaperReferenceMapper paperMapper = mock(PaperReferenceMapper.class);
        PaperProfileMapper profileMapper = mock(PaperProfileMapper.class);
        PaperSectionMapper sectionMapper = mock(PaperSectionMapper.class);
        PaperSectionSummaryMapper summaryMapper = mock(PaperSectionSummaryMapper.class);
        PaperChunkMapper chunkMapper = mock(PaperChunkMapper.class);
        LlmService llmService = mock(LlmService.class);
        KnowledgeBuildAsyncExecutor asyncExecutor = mock(KnowledgeBuildAsyncExecutor.class);

        PaperReference paper = new PaperReference();
        paper.setId(7L);
        paper.setTitle("Wind forecasting");
        paper.setPublishYear(2025);
        PaperProfile profile = new PaperProfile();
        profile.setId(8L);
        profile.setPaperId(7L);
        profile.setProfileVersion("paper-profile-v1");
        profile.setResearchProblem("Short-term wind power forecasting");
        profile.setMethodSummary("LSTM forecasting model");
        profile.setExperimentSummary("Evaluated on SCADA data");
        profile.setKeyContributions("Combines decomposition and forecasting");
        profile.setLimitations("Single-site validation");

        PaperSection section = new PaperSection();
        section.setId(10L);
        section.setPaperId(7L);
        section.setSectionType("EXPERIMENT");
        section.setSectionTitle("Experiments");
        section.setPageStart(7);
        PaperSectionSummary summary = new PaperSectionSummary();
        summary.setId(11L);
        summary.setPaperId(7L);
        summary.setSectionId(10L);
        summary.setSectionType("EXPERIMENT");
        summary.setSectionTitle("Experiments");
        summary.setSummaryVersion("section-summary-v1");
        summary.setSummary("The experiments use the NREL wind dataset.");
        summary.setKeyPoints("RMSE is used for evaluation.");
        PaperChunk chunk = new PaperChunk();
        chunk.setId(12L);
        chunk.setPaperId(7L);
        chunk.setSectionId(10L);
        chunk.setChunkIndex(0);
        chunk.setContent("The experiments use the NREL wind dataset for short-term forecasting.");
        chunk.setPageNumber(7);

        when(paperMapper.selectById(7L)).thenReturn(paper);
        when(profileMapper.selectOne(any())).thenReturn(profile);
        when(summaryMapper.selectList(any())).thenReturn(List.of(summary));
        when(sectionMapper.selectList(any())).thenReturn(List.of(section));
        when(chunkMapper.selectList(any())).thenReturn(List.of(chunk));
        when(catalogMapper.selectOne(any())).thenReturn(null);
        when(llmService.provider()).thenReturn("test-provider");
        when(llmService.modelName()).thenReturn("fake-knowledge");
        when(llmService.generateAnswer(any())).thenReturn("""
                [{
                  "knowledgeType":"DATASET",
                  "sectionId":10,
                  "subject":"Wind forecasting",
                  "predicate":"uses_dataset",
                  "objectValue":"NREL wind dataset",
                  "valueUnit":null,
                  "applicableCondition":"short-term forecasting",
                  "evidenceQuote":"The experiments use the NREL wind dataset"
                }]
                """);

        AtomicLong ids = new AtomicLong(1);
        List<PaperKnowledgeUnit> stored = new ArrayList<>();
        doAnswer(invocation -> {
            PaperCatalog catalog = invocation.getArgument(0);
            catalog.setId(ids.getAndIncrement());
            return 1;
        }).when(catalogMapper).insert(any(PaperCatalog.class));
        doAnswer((InvocationOnMock invocation) -> {
            PaperKnowledgeUnit unit = invocation.getArgument(0);
            unit.setId(ids.getAndIncrement());
            stored.add(unit);
            return 1;
        }).when(unitMapper).insert(any(PaperKnowledgeUnit.class));
        when(unitMapper.selectList(any())).thenAnswer(ignored -> new ArrayList<>(stored));
        when(unitMapper.selectCount(any())).thenReturn(0L);

        PaperKnowledgeServiceImpl service = new PaperKnowledgeServiceImpl(
                catalogMapper, unitMapper, feedbackMapper, paperMapper, profileMapper,
                sectionMapper, summaryMapper, chunkMapper, llmService, new ObjectMapper(), asyncExecutor);

        var response = service.build(7L);

        assertEquals("COMPLETED", response.getStatus());
        assertTrue(response.getKnowledgeCount() >= 7);
        PaperKnowledgeUnit dataset = stored.stream()
                .filter(unit -> "DATASET".equals(unit.getKnowledgeType()))
                .findFirst().orElse(null);
        assertNotNull(dataset);
        assertEquals("SILVER", dataset.getConfidenceLevel());
        assertEquals(12L, dataset.getChunkId());
        assertEquals(7, dataset.getPageNumber());
        assertNotNull(dataset.getNormalizedKey());
        assertTrue(stored.stream().noneMatch(unit -> Boolean.TRUE.equals(unit.getHasConflict())));

        dataset.setConfidenceLevel("GOLD");
        dataset.setExtractionMethod("USER");
        assertEquals(dataset.getId(), service.findUnits(List.of(7L), List.of(), 20).get(0).getId());
    }
}
