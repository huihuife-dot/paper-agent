package com.myagent.assistant.rag.service.impl;

import com.myagent.assistant.rag.dto.PaperRelevance;
import com.myagent.assistant.rag.dto.PaperCandidate;
import com.myagent.assistant.rag.dto.RagSource;
import com.myagent.assistant.rag.service.PaperDiscoveryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PaperDiscoveryService 单元测试。
 *
 * 覆盖：
 * - 空列表 / null 返回空
 * - 单篇论文聚合
 * - 多篇论文聚合（按相关度排序）
 * - hitCount / avgScore / maxScore 计算正确
 */
class PaperDiscoveryServiceImplTest {

    private PaperDiscoveryService service;

    @BeforeEach
    void setUp() {
        service = new PaperDiscoveryServiceImpl();
    }

    @Test
    void shouldReturnEmptyForNullSources() {
        List<PaperRelevance> result = service.aggregateByPaper(null, 5);
        assertTrue(result.isEmpty());
    }

    @Test
    void shouldReturnEmptyForEmptySources() {
        List<PaperRelevance> result = service.aggregateByPaper(List.of(), 5);
        assertTrue(result.isEmpty());
    }

    @Test
    void shouldAggregateSinglePaper() {
        List<RagSource> sources = new ArrayList<>();
        sources.add(buildSource(1L, "论文A", 0.9));
        sources.add(buildSource(1L, "论文A", 0.8));
        sources.add(buildSource(1L, "论文A", 0.7));

        List<PaperRelevance> result = service.aggregateByPaper(sources, 5);

        assertEquals(1, result.size());
        PaperRelevance pr = result.get(0);
        assertEquals(1L, pr.getPaperId());
        assertEquals("论文A", pr.getPaperTitle());
        assertEquals(3, pr.getHitCount());
        assertEquals(0.9, pr.getMaxScore(), 0.001);
        assertEquals(0.8, pr.getAvgScore(), 0.001);
        assertEquals(2, pr.getTopChunks().size());
    }

    @Test
    void shouldAggregateMultiplePapersSortedByRelevance() {
        List<RagSource> sources = new ArrayList<>();
        // 论文B 命中多 + 分数高 → 应该排第一
        sources.add(buildSource(2L, "论文B", 0.95));
        sources.add(buildSource(2L, "论文B", 0.90));
        sources.add(buildSource(2L, "论文B", 0.85));
        // 论文A 命中少 + 分数低 → 应该排第二
        sources.add(buildSource(1L, "论文A", 0.80));

        List<PaperRelevance> result = service.aggregateByPaper(sources, 5);

        assertEquals(2, result.size());
        // 论文B 排第一（hitCount=3, maxScore=0.95）
        assertEquals(2L, result.get(0).getPaperId());
        assertEquals("论文B", result.get(0).getPaperTitle());
        assertEquals(3, result.get(0).getHitCount());
        assertEquals(0.95, result.get(0).getMaxScore(), 0.001);
        // 论文A 排第二（hitCount=1, maxScore=0.80）
        assertEquals(1L, result.get(1).getPaperId());
        assertEquals("论文A", result.get(1).getPaperTitle());
        assertEquals(1, result.get(1).getHitCount());
    }

    @Test
    void shouldRespectTopPapers() {
        List<RagSource> sources = new ArrayList<>();
        sources.add(buildSource(1L, "论文A", 0.9));
        sources.add(buildSource(2L, "论文B", 0.8));
        sources.add(buildSource(3L, "论文C", 0.7));

        List<PaperRelevance> result = service.aggregateByPaper(sources, 2);

        assertEquals(2, result.size());
    }

    @Test
    void shouldHandleSourcesWithoutTitle() {
        RagSource source = new RagSource();
        source.setChunkId(1L);
        source.setPaperId(1L);
        source.setPaperTitle(null); // 无标题
        source.setScore(0.9);
        source.setContent("test content");

        List<PaperRelevance> result = service.aggregateByPaper(List.of(source), 5);

        assertEquals(1, result.size());
        assertEquals("未知论文", result.get(0).getPaperTitle());
    }

    @Test
    void shouldSkipSourcesWithoutPaperId() {
        RagSource source = new RagSource();
        source.setChunkId(1L);
        source.setPaperId(null);
        source.setScore(0.9);
        source.setContent("test");

        List<PaperRelevance> result = service.aggregateByPaper(List.of(source), 5);

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldRankCrossTypeEvidenceAboveRepeatedSingleTypeHits() {
        List<RagSource> sources = new ArrayList<>();
        sources.add(buildTypedSource(1L, "paper_profile", 1.0));
        sources.add(buildTypedSource(1L, "section_summary", 0.8));
        sources.add(buildTypedSource(1L, "raw_chunk", 0.7));
        sources.add(buildTypedSource(2L, "section_summary", 1.0));
        sources.add(buildTypedSource(2L, "section_summary", 0.95));
        sources.add(buildTypedSource(2L, "section_summary", 0.90));

        List<Long> ranked = service.rankPaperIdsByEvidence(sources, 2);

        assertEquals(List.of(1L, 2L), ranked);
    }

    @Test
    void shouldNormalizeScoresWithinEachContentType() {
        List<RagSource> sources = List.of(
                buildTypedSource(1L, "paper_profile", 0.05),
                buildTypedSource(2L, "section_summary", 0.05),
                buildTypedSource(2L, "raw_chunk", 0.04)
        );

        List<Long> ranked = service.rankPaperIdsByEvidence(sources, 2);

        assertEquals(List.of(2L, 1L), ranked);
    }

    @Test
    void shouldUseStablePaperIdTieBreakForCandidateRanking() {
        List<RagSource> sources = List.of(
                buildTypedSource(2L, "paper_profile", 0.8),
                buildTypedSource(1L, "paper_profile", 0.8)
        );

        assertEquals(List.of(1L, 2L), service.rankPaperIdsByEvidence(sources, 2));
    }

    @Test
    void shouldBoostPaperWithDirectQueryTermEvidence() {
        RagSource directEvidence = buildTypedSource(2L, "section_summary", 0.8);
        directEvidence.setContent("The proposed VMD decomposition is followed by LSTM forecasting.");
        RagSource genericEvidence = buildTypedSource(1L, "section_summary", 0.8);
        genericEvidence.setContent("A generic forecasting framework for renewable energy.");

        List<PaperCandidate> candidates = service.rankCandidatesByEvidence(
                List.of(genericEvidence, directEvidence),
                "哪些论文明确使用VMD？",
                List.of("VMD"),
                2);

        assertEquals(2L, candidates.get(0).paperId());
        assertEquals(1.0, candidates.get(0).queryTermCoverage(), 0.001);
    }

    @Test
    void shouldPenalizeReviewForOriginalMethodQuestionButNotReviewQuestion() {
        RagSource review = buildTypedSource(1L, "section_summary", 0.8);
        review.setPaperTitle("A comprehensive review of forecasting methods");
        RagSource original = buildTypedSource(2L, "section_summary", 0.8);
        original.setPaperTitle("A novel forecasting model");

        List<PaperCandidate> methodCandidates = service.rankCandidatesByEvidence(
                List.of(review, original), "哪些论文明确使用该方法？", List.of(), 2);
        List<PaperCandidate> reviewCandidates = service.rankCandidatesByEvidence(
                List.of(review, original), "哪篇是综述论文？", List.of(), 2);

        assertEquals(2L, methodCandidates.get(0).paperId());
        assertEquals(1L, reviewCandidates.get(0).paperId());
    }

    private RagSource buildSource(Long paperId, String paperTitle, Double score) {
        RagSource source = new RagSource();
        source.setPaperId(paperId);
        source.setPaperTitle(paperTitle);
        source.setChunkId(paperId * 100 + (long) (Math.random() * 100));
        source.setScore(score);
        source.setContent("Content for paper " + paperId);
        source.setRetrievalRoute("test");
        return source;
    }

    private RagSource buildTypedSource(Long paperId, String sourceType, Double score) {
        RagSource source = buildSource(paperId, "Paper " + paperId, score);
        source.setSourceType(sourceType);
        if ("paper_profile".equals(sourceType)) {
            source.setProfileId(paperId * 1000 + 1);
            source.setChunkId(null);
        } else if ("section_summary".equals(sourceType)) {
            source.setSectionSummaryId(paperId * 1000 + (long) (Math.random() * 100));
            source.setChunkId(null);
        }
        return source;
    }
}
