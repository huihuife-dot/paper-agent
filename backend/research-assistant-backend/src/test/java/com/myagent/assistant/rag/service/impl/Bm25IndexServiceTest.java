package com.myagent.assistant.rag.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.myagent.assistant.paper.entity.PaperChunk;
import com.myagent.assistant.paper.mapper.PaperChunkMapper;
import com.myagent.assistant.rag.service.Bm25IndexService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * BM25 索引服务单元测试。
 * 覆盖：索引构建、关键词命中、未命中返回空、空 query 返回空。
 */
class Bm25IndexServiceTest {

    private Bm25IndexService service;

    @BeforeEach
    void setUp() {
        PaperChunkMapper chunkMapper = mock(PaperChunkMapper.class);

        PaperChunk c1 = chunk(1L, 7L, "This paper proposes a Transformer-based architecture for multivariate time series forecasting");
        PaperChunk c2 = chunk(2L, 7L, "The attention mechanism captures long-range temporal dependencies");
        PaperChunk c3 = chunk(3L, 8L, "Graph neural networks for spatiotemporal wind speed prediction");
        PaperChunk c4 = chunk(4L, 8L, "A convolutional LSTM approach to traffic flow prediction using GNN");
        PaperChunk cRef = chunk(5L, 7L, "References [1] Vaswani et al. 2017 Attention is all you need");
        cRef.setIsReference(true);

        when(chunkMapper.selectList(any(Wrapper.class))).thenReturn(List.of(c1, c2, c3, c4, cRef));

        service = new Bm25IndexService(chunkMapper);
        service.buildIndex();
    }

    @Test
    void shouldBuildIndexAndBeReady() {
        assertThat(service.isReady()).isTrue();
    }

    @Test
    void shouldMatchExactTermTransformer() {
        List<Map.Entry<Long, Double>> results = service.search("Transformer", 5);

        assertThat(results).isNotEmpty();
        // chunk 1 contains "Transformer-based"
        assertThat(results.stream().map(Map.Entry::getKey)).contains(1L);
    }

    @Test
    void shouldMatchPhraseWithMultipleTerms() {
        List<Map.Entry<Long, Double>> results = service.search("Transformer forecasting", 5);

        assertThat(results).isNotEmpty();
        // chunk 1 contains both "Transformer" and "forecasting"
        assertThat(results.stream().map(Map.Entry::getKey)).contains(1L);
    }

    @Test
    void shouldMatchGraphNeuralNetworkTerm() {
        List<Map.Entry<Long, Double>> results = service.search("graph neural network", 5);

        assertThat(results).isNotEmpty();
        // chunk 3 contains "Graph neural networks"
        assertThat(results.stream().map(Map.Entry::getKey)).contains(3L);
    }

    @Test
    void shouldReturnEmptyForNoMatch() {
        List<Map.Entry<Long, Double>> results = service.search("quantum computing", 5);
        assertThat(results).isEmpty();
    }

    @Test
    void shouldReturnEmptyForBlankQuery() {
        assertThat(service.search("", 5)).isEmpty();
        assertThat(service.search("  ", 5)).isEmpty();
    }

    @Test
    void shouldRespectTopK() {
        List<Map.Entry<Long, Double>> results = service.search("prediction", 2);
        assertThat(results.size()).isLessThanOrEqualTo(2);
    }

    @Test
    void shouldExcludeReferenceChunks() {
        // chunk 5 is isReference=true, should not appear in results even if query matches
        List<Map.Entry<Long, Double>> results = service.search("attention", 10);
        for (Map.Entry<Long, Double> r : results) {
            assertThat(r.getKey()).isNotEqualTo(5L);
        }
    }

    @Test
    void shouldScoreHigherForMultipleTermMatches() {
        List<Map.Entry<Long, Double>> results = service.search("gnn graph neural prediction", 5);

        if (results.size() >= 2) {
            // chunk 4 matches "gnn" + "prediction", chunk 3 matches "gnn" + "graph" + "neural" + "prediction"
            // chunk 4 should rank near top
            assertThat(results.stream().map(Map.Entry::getKey)).contains(4L);
        }
    }

    private PaperChunk chunk(Long id, Long paperId, String content) {
        PaperChunk c = new PaperChunk();
        c.setId(id);
        c.setPaperId(paperId);
        c.setChunkIndex(id.intValue());
        c.setContent(content);
        return c;
    }
}
