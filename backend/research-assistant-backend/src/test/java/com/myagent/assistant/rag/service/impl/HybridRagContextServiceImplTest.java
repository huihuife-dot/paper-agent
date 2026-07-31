package com.myagent.assistant.rag.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.myagent.assistant.paper.entity.PaperProfile;
import com.myagent.assistant.paper.entity.PaperReference;
import com.myagent.assistant.paper.entity.PaperSectionSummary;
import com.myagent.assistant.paper.mapper.PaperProfileMapper;
import com.myagent.assistant.paper.mapper.PaperReferenceMapper;
import com.myagent.assistant.paper.mapper.PaperSectionSummaryMapper;
import com.myagent.assistant.rag.context.HybridRagContext;
import com.myagent.assistant.rag.context.HybridRagContextRequest;
import com.myagent.assistant.rag.dto.RagSource;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HybridRagContextServiceImplTest {

    @Test
    void buildContextIncludesProfilesForEachPaperInInputOrder() {
        PaperReferenceMapper paperReferenceMapper = mock(PaperReferenceMapper.class);
        PaperProfileMapper paperProfileMapper = mock(PaperProfileMapper.class);
        PaperSectionSummaryMapper paperSectionSummaryMapper = mock(PaperSectionSummaryMapper.class);

        when(paperReferenceMapper.selectById(7L)).thenReturn(paper(7L, "Paper A"));
        when(paperReferenceMapper.selectById(8L)).thenReturn(paper(8L, "Paper B"));
        when(paperProfileMapper.selectOne(any(Wrapper.class))).thenReturn(
                profile(70L, 7L, "Paper A method"),
                profile(80L, 8L, "Paper B method")
        );
        when(paperSectionSummaryMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        HybridRagContextServiceImpl service = new HybridRagContextServiceImpl(
                paperReferenceMapper,
                paperProfileMapper,
                paperSectionSummaryMapper
        );

        HybridRagContext context = service.buildContext(new HybridRagContextRequest(
                "对比方法",
                List.of(7L, 8L),
                List.of(),
                4,
                2
        ));

        assertThat(context.getContextText()).containsSubsequence("[Paper 7] Paper A", "[Paper 8] Paper B");
        assertThat(context.getSources())
                .filteredOn(source -> "paper_profile".equals(source.getSourceType()))
                .extracting(RagSource::getPaperId)
                .containsExactly(7L, 8L);
    }

    @Test
    void methodQuestionPrioritizesMethodSummaryAndLimitsCount() {
        PaperReferenceMapper paperReferenceMapper = mock(PaperReferenceMapper.class);
        PaperProfileMapper paperProfileMapper = mock(PaperProfileMapper.class);
        PaperSectionSummaryMapper paperSectionSummaryMapper = mock(PaperSectionSummaryMapper.class);

        when(paperReferenceMapper.selectById(7L)).thenReturn(paper(7L, "Paper A"));
        when(paperProfileMapper.selectOne(any(Wrapper.class))).thenReturn(profile(70L, 7L, "Paper A method"));
        when(paperSectionSummaryMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                summary(1L, 7L, "EXPERIMENT", "Experiment summary"),
                summary(2L, 7L, "METHOD", "Method summary"),
                summary(3L, 7L, "INTRODUCTION", "Introduction summary"),
                summary(4L, 7L, "RESULT", "Result summary"),
                summary(5L, 7L, "DISCUSSION", "Discussion summary")
        ));

        HybridRagContextServiceImpl service = new HybridRagContextServiceImpl(
                paperReferenceMapper,
                paperProfileMapper,
                paperSectionSummaryMapper
        );

        HybridRagContext context = service.buildContext(new HybridRagContextRequest(
                "这篇论文的方法是什么？",
                List.of(7L),
                List.of(),
                2,
                2
        ));

        List<RagSource> summarySources = context.getSources().stream()
                .filter(source -> "section_summary".equals(source.getSourceType()))
                .toList();
        assertThat(summarySources).hasSize(2);
        assertThat(summarySources).extracting(RagSource::getSectionType).contains("METHOD");
    }

    @Test
    void rawChunkSourcesAreGroupedAndLimitedPerPaper() {
        PaperReferenceMapper paperReferenceMapper = mock(PaperReferenceMapper.class);
        PaperProfileMapper paperProfileMapper = mock(PaperProfileMapper.class);
        PaperSectionSummaryMapper paperSectionSummaryMapper = mock(PaperSectionSummaryMapper.class);

        when(paperReferenceMapper.selectById(7L)).thenReturn(paper(7L, "Paper A"));
        when(paperReferenceMapper.selectById(8L)).thenReturn(paper(8L, "Paper B"));
        when(paperProfileMapper.selectOne(any(Wrapper.class))).thenReturn(
                profile(70L, 7L, "Paper A method"),
                profile(80L, 8L, "Paper B method")
        );
        when(paperSectionSummaryMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        HybridRagContextServiceImpl service = new HybridRagContextServiceImpl(
                paperReferenceMapper,
                paperProfileMapper,
                paperSectionSummaryMapper
        );

        HybridRagContext context = service.buildContext(new HybridRagContextRequest(
                "对比方法",
                List.of(7L, 8L),
                List.of(
                        rawSource(7L, 101L, 0.91),
                        rawSource(7L, 102L, 0.93),
                        rawSource(7L, 103L, 0.89),
                        rawSource(8L, 201L, 0.88)
                ),
                4,
                2
        ));

        assertThat(context.getSources())
                .filteredOn(source -> "raw_chunk".equals(source.getSourceType()) && Long.valueOf(7L).equals(source.getPaperId()))
                .extracting(RagSource::getChunkId)
                .containsExactly(102L, 101L);
        assertThat(context.getSources())
                .filteredOn(source -> "raw_chunk".equals(source.getSourceType()) && Long.valueOf(8L).equals(source.getPaperId()))
                .extracting(RagSource::getChunkId)
                .containsExactly(201L);
    }

    private PaperReference paper(Long id, String title) {
        PaperReference paper = new PaperReference();
        paper.setId(id);
        paper.setTitle(title);
        return paper;
    }

    private PaperProfile profile(Long id, Long paperId, String methodSummary) {
        PaperProfile profile = new PaperProfile();
        profile.setId(id);
        profile.setPaperId(paperId);
        profile.setTitle("Paper " + paperId);
        profile.setResearchProblem("Research problem " + paperId);
        profile.setMethodSummary(methodSummary);
        profile.setExperimentSummary("Experiment " + paperId);
        profile.setKeyContributions("Contribution " + paperId);
        profile.setLimitations("Limitation " + paperId);
        profile.setKeywords("keyword" + paperId);
        profile.setProfileText("Profile text " + paperId);
        profile.setProfileVersion("paper-profile-v1");
        return profile;
    }

    private PaperSectionSummary summary(Long id, Long paperId, String sectionType, String summaryText) {
        PaperSectionSummary summary = new PaperSectionSummary();
        summary.setId(id);
        summary.setPaperId(paperId);
        summary.setSectionId(id * 10);
        summary.setSectionType(sectionType);
        summary.setSectionTitle(sectionType + " title");
        summary.setSummary(summaryText);
        summary.setKeyPoints(sectionType + " key points");
        summary.setSummaryVersion("section-summary-v1");
        return summary;
    }

    private RagSource rawSource(Long paperId, Long chunkId, Double score) {
        RagSource source = new RagSource();
        source.setPaperId(paperId);
        source.setPaperTitle("Paper " + paperId);
        source.setChunkId(chunkId);
        source.setChunkIndex(chunkId.intValue());
        source.setSectionType("METHOD");
        source.setSectionTitle("Method");
        source.setScore(score);
        source.setContent("Raw chunk " + chunkId);
        return source;
    }
}
