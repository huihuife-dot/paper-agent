package com.myagent.assistant.researchengineering.service.impl;

import com.myagent.assistant.idea.service.ResearchIdeaService;
import com.myagent.assistant.idea.entity.ResearchIdea;
import com.myagent.assistant.paper.entity.PaperReference;
import com.myagent.assistant.paper.entity.PaperSectionSummary;
import com.myagent.assistant.paper.entity.PaperChunk;
import com.myagent.assistant.paper.mapper.PaperProfileMapper;
import com.myagent.assistant.paper.mapper.PaperSectionSummaryMapper;
import com.myagent.assistant.paper.mapper.PaperChunkMapper;
import com.myagent.assistant.paper.service.PaperReferenceService;
import com.myagent.assistant.paper.service.PaperReproductionSpecService;
import com.myagent.assistant.researchengineering.dto.PaperReproductionContextResponse;
import com.myagent.assistant.researchengineering.dto.IdeaImprovementContextResponse;
import com.myagent.assistant.paper.reproduction.ReproductionSpecDocument;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ResearchEngineeringContextServiceImplTest {

    @Test
    void paperContextPrioritizesAndBoundsSectionEvidence() {
        ResearchIdeaService ideaService = mock(ResearchIdeaService.class);
        PaperReferenceService paperService = mock(PaperReferenceService.class);
        PaperProfileMapper profileMapper = mock(PaperProfileMapper.class);
        PaperSectionSummaryMapper summaryMapper = mock(PaperSectionSummaryMapper.class);
        ResearchEngineeringContextServiceImpl service = new ResearchEngineeringContextServiceImpl(
                ideaService, paperService, profileMapper, summaryMapper,
                mock(PaperChunkMapper.class), mock(PaperReproductionSpecService.class));
        PaperReference paper = new PaperReference();
        paper.setId(38L);
        paper.setTitle("Bounded paper");
        paper.setAbstractText("abstract");
        paper.setUploadTime(LocalDateTime.of(2026, 7, 22, 10, 0));
        when(paperService.getPaperById(38L)).thenReturn(paper);
        when(profileMapper.selectOne(any())).thenReturn(null);

        List<PaperSectionSummary> summaries = new ArrayList<>();
        for (int index = 0; index < 20; index++) {
            PaperSectionSummary summary = new PaperSectionSummary();
            summary.setId((long) index + 1);
            summary.setSectionType(index == 19 ? "METHODS" : "INTRODUCTION");
            summary.setSectionTitle(index == 19 ? "Method" : "Introduction");
            summary.setSummary("x".repeat(2_000));
            summaries.add(summary);
        }
        when(summaryMapper.selectList(any())).thenReturn(summaries);

        PaperReproductionContextResponse response = service.getPaperReproductionContext(38L);

        // One metadata evidence + at most sixteen selected section summaries.
        assertEquals(17, response.getEvidence().size());
        assertEquals("Method", response.getEvidence().get(1).getTitle());
        assertTrue(response.getEvidence().get(1).getContent().contains("[truncated by MyAgent context budget]"));
    }

    @Test
    void v2AddsRawEvidenceAndTrustedFactsWhileV1RemainsReadable() {
        ResearchIdeaService ideaService = mock(ResearchIdeaService.class);
        PaperReferenceService paperService = mock(PaperReferenceService.class);
        PaperProfileMapper profileMapper = mock(PaperProfileMapper.class);
        PaperSectionSummaryMapper summaryMapper = mock(PaperSectionSummaryMapper.class);
        PaperChunkMapper chunkMapper = mock(PaperChunkMapper.class);
        PaperReproductionSpecService specService = mock(PaperReproductionSpecService.class);
        ResearchEngineeringContextServiceImpl service = new ResearchEngineeringContextServiceImpl(
                ideaService, paperService, profileMapper, summaryMapper, chunkMapper, specService);
        PaperReference paper = new PaperReference();
        paper.setId(38L); paper.setTitle("LSTM paper"); paper.setAbstractText("abstract");
        paper.setUploadTime(LocalDateTime.of(2026, 7, 27, 10, 0));
        when(paperService.getPaperById(38L)).thenReturn(paper);
        when(profileMapper.selectOne(any())).thenReturn(null);
        when(summaryMapper.selectList(any())).thenReturn(List.of());
        PaperChunk chunk = new PaperChunk();
        chunk.setId(8L); chunk.setPaperId(38L); chunk.setSectionType("METHOD");
        chunk.setSectionTitle("Method"); chunk.setContent("The batch size is 32.");
        chunk.setIsReference(false); chunk.setIsNoise(false); chunk.setPageStart(4);
        when(chunkMapper.selectList(any())).thenReturn(List.of(chunk));
        ReproductionSpecDocument.FactItem trusted = new ReproductionSpecDocument.FactItem(
                9L, "HYPERPARAMETER", "batch_size", "32", "samples", "{}",
                "TABLE", 77L, 4, "Batch size is 32", 1.0, "USER_CONFIRMED");
        ReproductionSpecDocument spec = new ReproductionSpecDocument(
                "reproduction-spec-v1", 38L, "revision-v2", "PARTIAL", 0.5, 1.0,
                List.of(trusted), List.of(), List.of(), List.of(),
                List.of("dataset: no trusted evidence"),
                List.of("Use synthetic input only for smoke checks."));
        when(specService.get(38L)).thenReturn(spec);

        PaperReproductionContextResponse v2 = service.getPaperReproductionContext(38L, 2);
        PaperReproductionContextResponse v1 = service.getPaperReproductionContext(38L, 1);

        assertEquals("agent-context-v2", v2.getProtocolVersion());
        assertEquals("agent-task-package-v2", v2.getTaskPackage().getProtocolVersion());
        assertEquals("revision-v2", v2.getSourceRevision());
        assertTrue(v2.getEvidence().stream().anyMatch(item -> "paper_raw_chunk".equals(item.getKind())));
        assertTrue(v2.getEvidence().stream().anyMatch(item -> "paper_reproduction_fact".equals(item.getKind())));
        assertEquals("agent-context-v1", v1.getProtocolVersion());
        assertTrue(v1.getEvidence().stream().noneMatch(item -> "paper_raw_chunk".equals(item.getKind())));
        assertEquals(null, v1.getReproductionSpec());
    }

    @Test
    void ideaContextAddsBoundedTrustedFactsAndRawEvidenceFromRelatedPapers() {
        ResearchIdeaService ideaService = mock(ResearchIdeaService.class);
        PaperReferenceService paperService = mock(PaperReferenceService.class);
        PaperProfileMapper profileMapper = mock(PaperProfileMapper.class);
        PaperSectionSummaryMapper summaryMapper = mock(PaperSectionSummaryMapper.class);
        PaperChunkMapper chunkMapper = mock(PaperChunkMapper.class);
        PaperReproductionSpecService specService = mock(PaperReproductionSpecService.class);
        ResearchEngineeringContextServiceImpl service = new ResearchEngineeringContextServiceImpl(
                ideaService, paperService, profileMapper, summaryMapper, chunkMapper, specService);
        ResearchIdea idea = new ResearchIdea();
        idea.setId(17L);
        idea.setTitle("Add an LSTM prediction module");
        idea.setOriginalContent("Use the paper's LSTM structure in the existing repository.");
        idea.setPossibleMethod("Add LSTM without changing the existing input API.");
        idea.setRelatedPaperIds("38");
        idea.setCreateTime(LocalDateTime.of(2026, 7, 29, 10, 0));
        when(ideaService.getById(17L)).thenReturn(idea);
        PaperReference paper = new PaperReference();
        paper.setId(38L);
        paper.setTitle("LSTM paper");
        paper.setAbstractText("An LSTM method.");
        when(paperService.getPaperById(38L)).thenReturn(paper);
        ReproductionSpecDocument.FactItem trusted = new ReproductionSpecDocument.FactItem(
                9L, "ARCHITECTURE", "lstm_layers", "2", null, "{}",
                "FIGURE", 77L, 4, "The architecture contains two LSTM layers.",
                1.0, "USER_CONFIRMED");
        when(specService.get(38L)).thenReturn(new ReproductionSpecDocument(
                "reproduction-spec-v1", 38L, "revision-v2", "PARTIAL", 0.5, 1.0,
                List.of(trusted), List.of(), List.of(), List.of(),
                List.of("dataset: no trusted evidence"), List.of()));
        PaperChunk chunk = new PaperChunk();
        chunk.setId(8L);
        chunk.setPaperId(38L);
        chunk.setSectionType("METHOD");
        chunk.setSectionTitle("LSTM Method");
        chunk.setContent("The LSTM module receives the decomposed sequence.");
        chunk.setPageStart(4);
        chunk.setIsReference(false);
        chunk.setIsNoise(false);
        when(chunkMapper.selectList(any())).thenReturn(List.of(chunk));

        IdeaImprovementContextResponse response = service.getIdeaImprovementContext(17L);

        assertTrue(response.getEvidence().stream().anyMatch(item ->
                "idea_related_paper_fact".equals(item.getKind())
                        && "USER_CONFIRMED".equals(item.getVerificationStatus())));
        assertTrue(response.getEvidence().stream().anyMatch(item ->
                "idea_related_paper_raw_chunk".equals(item.getKind())));
        assertTrue(response.getTaskPackage().getImplementationSteps().stream().anyMatch(item ->
                item.contains("Idea as the target")));
    }
}
