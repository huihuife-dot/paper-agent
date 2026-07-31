package com.myagent.assistant.rag.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myagent.assistant.paper.entity.PaperChunk;
import com.myagent.assistant.paper.entity.PaperReference;
import com.myagent.assistant.paper.mapper.PaperChunkMapper;
import com.myagent.assistant.paper.mapper.PaperReferenceMapper;
import com.myagent.assistant.qdrant.service.QdrantService;
import com.myagent.assistant.rag.dto.RagSource;
import com.myagent.assistant.rag.dto.PaperCandidate;
import com.myagent.assistant.rag.service.Bm25IndexService;
import com.myagent.assistant.rag.service.PaperDiscoveryService;
import com.myagent.assistant.rag.service.QueryRewriteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RagRetrievalServiceImplTest {

    private QdrantService qdrantService;
    private PaperChunkMapper paperChunkMapper;
    private PaperReferenceMapper paperReferenceMapper;
    private QueryRewriteService queryRewriteService;
    private Bm25IndexService bm25IndexService;
    private PaperDiscoveryService paperDiscoveryService;
    private RagRetrievalServiceImpl service;

    @BeforeEach
    void setUp() {
        qdrantService = mock(QdrantService.class);
        paperChunkMapper = mock(PaperChunkMapper.class);
        paperReferenceMapper = mock(PaperReferenceMapper.class);
        queryRewriteService = mock(QueryRewriteService.class);
        bm25IndexService = mock(Bm25IndexService.class);
        paperDiscoveryService = new PaperDiscoveryServiceImpl();
        when(bm25IndexService.isReady()).thenReturn(false); // BM25 not loaded in unit test
        when(bm25IndexService.search("english query", 15)).thenReturn(List.of());
        when(bm25IndexService.search("method question", 6)).thenReturn(List.of());
        service = new RagRetrievalServiceImpl(
                qdrantService, paperChunkMapper, paperReferenceMapper,
                new ObjectMapper(), queryRewriteService, bm25IndexService, paperDiscoveryService);
    }

    @Test
    void retrieveSourcesPassesPaperScopeToOriginalAndRewrittenSearches() throws Exception {
        when(queryRewriteService.rewriteForRetrieval("中文问题")).thenReturn("english query");
        when(qdrantService.searchSimilarChunksRaw("中文问题", 15, List.of(3L, 5L), "RAW_CHUNK")).thenReturn("""
                {"result":[{"score":0.6,"payload":{"chunkId":30}}]}
                """);
        when(qdrantService.searchSimilarChunksRaw("english query", 15, List.of(3L, 5L), "RAW_CHUNK")).thenReturn("""
                {"result":[{"score":0.8,"payload":{"chunkId":50}}]}
                """);

        PaperChunk chunk30 = chunk(30L, 3L, 1, "paper 3 chunk");
        PaperChunk chunk50 = chunk(50L, 5L, 2, "paper 5 chunk");
        when(paperChunkMapper.selectById(30L)).thenReturn(chunk30);
        when(paperChunkMapper.selectById(50L)).thenReturn(chunk50);
        when(paperReferenceMapper.selectById(3L)).thenReturn(paper(3L, "Paper 3"));
        when(paperReferenceMapper.selectById(5L)).thenReturn(paper(5L, "Paper 5"));

        List<RagSource> sources = service.retrieveSources("中文问题", 5, Arrays.asList(3L, 5L, 3L, 0L, null));

        assertThat(sources).hasSize(2);
        verify(qdrantService).searchSimilarChunksRaw("中文问题", 15, List.of(3L, 5L), "RAW_CHUNK");
        verify(qdrantService).searchSimilarChunksRaw("english query", 15, List.of(3L, 5L), "RAW_CHUNK");
    }

    @Test
    void retrieveSourcesFiltersReferenceChunksAfterMysqlLookup() throws Exception {
        when(queryRewriteService.rewriteForRetrieval("method question")).thenReturn("method question");
        when(qdrantService.searchSimilarChunksRaw("method question", 6, List.of(), "RAW_CHUNK")).thenReturn("""
                {"result":[
                  {"score":0.9,"payload":{"chunkId":1}},
                  {"score":0.8,"payload":{"chunkId":2}}
                ]}
                """);

        PaperChunk referenceChunk = new PaperChunk();
        referenceChunk.setId(1L);
        referenceChunk.setPaperId(10L);
        referenceChunk.setChunkIndex(1);
        referenceChunk.setContent("[1] reference item");
        referenceChunk.setIsReference(true);
        referenceChunk.setIsNoise(false);

        PaperChunk methodChunk = chunk(2L, 10L, 2, "method content");
        methodChunk.setSectionId(5L);
        methodChunk.setSectionTitle("Proposed Method");
        methodChunk.setSectionType("METHOD");
        methodChunk.setIsReference(false);
        methodChunk.setIsNoise(false);
        methodChunk.setChunkStrategyVersion("paper-structure-v1");

        when(paperChunkMapper.selectById(1L)).thenReturn(referenceChunk);
        when(paperChunkMapper.selectById(2L)).thenReturn(methodChunk);
        when(paperReferenceMapper.selectById(10L)).thenReturn(paper(10L, "Paper"));

        List<RagSource> sources = service.retrieveSources("method question", 2);

        assertThat(sources).hasSize(1);
        assertThat(sources.get(0).getChunkId()).isEqualTo(2L);
        assertThat(sources.get(0).getSectionType()).isEqualTo("METHOD");
        assertThat(sources.get(0).getSectionTitle()).isEqualTo("Proposed Method");
    }

    @Test
    void retrieveSourcesFiltersBm25HitsBySelectedPaperScope() throws Exception {
        when(bm25IndexService.isReady()).thenReturn(true);
        when(queryRewriteService.rewriteForRetrieval("forecasting method")).thenReturn("forecasting method");
        when(qdrantService.searchSimilarChunksRaw(
                "forecasting method", 9, List.of(3L), "RAW_CHUNK"))
                .thenReturn("{\"result\":[]}");
        when(bm25IndexService.search("forecasting method", 9))
                .thenReturn(List.of(
                        java.util.Map.entry(30L, 4.0),
                        java.util.Map.entry(50L, 3.0)));

        when(paperChunkMapper.selectById(30L)).thenReturn(chunk(30L, 3L, 1, "selected paper"));
        when(paperChunkMapper.selectById(50L)).thenReturn(chunk(50L, 5L, 1, "unselected paper"));
        when(paperReferenceMapper.selectById(3L)).thenReturn(paper(3L, "Paper 3"));

        List<RagSource> sources = service.retrieveSources("forecasting method", 3, List.of(3L));

        assertThat(sources).extracting(RagSource::getPaperId).containsExactly(3L);
    }

    @Test
    void libraryDiscoveryKeepsProfileAndSectionSummaryInRrfResults() throws Exception {
        com.myagent.assistant.rag.dto.QueryRewriteResult rewrite =
                new com.myagent.assistant.rag.dto.QueryRewriteResult();
        rewrite.setOriginalQuestion("find papers");
        rewrite.setDeclarativeQuery("time series forecasting");
        rewrite.setKeywordQuery("time series forecasting");
        rewrite.setHydeQuery("A forecasting paper.");

        String response = """
                {"result":[
                  {"score":0.9,"payload":{"contentType":"PAPER_PROFILE","paperId":3,"profileId":101,"text":"profile"}},
                  {"score":0.8,"payload":{"contentType":"SECTION_SUMMARY","paperId":3,"sectionSummaryId":201,"text":"summary"}}
                ]}
                """;
        when(qdrantService.searchSimilarChunksByContentTypesRaw(
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(6),
                org.mockito.ArgumentMatchers.eq(List.of()),
                org.mockito.ArgumentMatchers.eq(List.of("PAPER_PROFILE", "SECTION_SUMMARY", "RAW_CHUNK"))))
                .thenReturn(java.util.Map.of(
                        "PAPER_PROFILE", response,
                        "SECTION_SUMMARY", response,
                        "RAW_CHUNK", "{\"result\":[]}"
                ));
        when(paperReferenceMapper.selectById(3L)).thenReturn(paper(3L, "Paper 3"));

        List<RagSource> sources = service.retrieveSourcesWithRewrite(rewrite, 2, List.of());

        assertThat(sources).extracting(RagSource::getSourceType)
                .containsExactlyInAnyOrder("paper_profile", "section_summary");
    }

    @Test
    void libraryDiscoveryCoversMultipleCandidatePapersBeforeAddingMoreEvidence() throws Exception {
        com.myagent.assistant.rag.dto.QueryRewriteResult rewrite =
                new com.myagent.assistant.rag.dto.QueryRewriteResult();
        rewrite.setOriginalQuestion("find graph papers");
        rewrite.setDeclarativeQuery("graph forecasting papers");
        rewrite.setKeywordQuery("graph forecasting");
        rewrite.setHydeQuery("Graph forecasting methods.");

        String profiles = """
                {"result":[
                  {"score":0.95,"payload":{"contentType":"PAPER_PROFILE","paperId":1,"profileId":101,"text":"profile 1"}},
                  {"score":0.90,"payload":{"contentType":"PAPER_PROFILE","paperId":2,"profileId":102,"text":"profile 2"}},
                  {"score":0.85,"payload":{"contentType":"PAPER_PROFILE","paperId":3,"profileId":103,"text":"profile 3"}}
                ]}
                """;
        String summaries = """
                {"result":[
                  {"score":0.99,"payload":{"contentType":"SECTION_SUMMARY","paperId":1,"sectionSummaryId":201,"text":"summary 1a"}},
                  {"score":0.98,"payload":{"contentType":"SECTION_SUMMARY","paperId":1,"sectionSummaryId":202,"text":"summary 1b"}},
                  {"score":0.80,"payload":{"contentType":"SECTION_SUMMARY","paperId":2,"sectionSummaryId":203,"text":"summary 2"}},
                  {"score":0.75,"payload":{"contentType":"SECTION_SUMMARY","paperId":3,"sectionSummaryId":204,"text":"summary 3"}}
                ]}
                """;
        when(qdrantService.searchSimilarChunksByContentTypesRaw(
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(18),
                org.mockito.ArgumentMatchers.eq(List.of()), org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(java.util.Map.of(
                        "PAPER_PROFILE", profiles,
                        "SECTION_SUMMARY", summaries,
                        "RAW_CHUNK", "{\"result\":[]}"
                ));
        when(paperReferenceMapper.selectById(org.mockito.ArgumentMatchers.anyLong()))
                .thenAnswer(invocation -> paper(invocation.getArgument(0), "Paper"));

        List<RagSource> sources = service.retrieveSourcesWithRewrite(rewrite, 6, List.of());

        assertThat(sources).hasSize(6);
        assertThat(sources.subList(0, 3)).extracting(RagSource::getPaperId)
                .containsExactly(1L, 2L, 3L);
        assertThat(sources).extracting(RagSource::getPaperId)
                .contains(1L, 2L, 3L);
    }

    @Test
    void libraryDiscoveryDropsWeakCandidatesForSingleTargetQuestion() throws Exception {
        com.myagent.assistant.rag.dto.QueryRewriteResult rewrite =
                new com.myagent.assistant.rag.dto.QueryRewriteResult();
        rewrite.setOriginalQuestion("文献库中唯一哪篇论文研究光伏功率？");
        rewrite.setDeclarativeQuery("photovoltaic power forecasting paper");
        rewrite.setKeywordQuery("photovoltaic PV LSTM");
        rewrite.setKeywordTerms("photovoltaic,PV,LSTM");
        rewrite.setHydeQuery("The paper forecasts photovoltaic power with LSTM.");

        String profiles = """
                {"result":[
                  {"score":0.95,"payload":{"contentType":"PAPER_PROFILE","paperId":1,"profileId":101,"text":"PV profile"}},
                  {"score":0.30,"payload":{"contentType":"PAPER_PROFILE","paperId":2,"profileId":102,"text":"wind profile"}}
                ]}
                """;
        String summaries = """
                {"result":[
                  {"score":0.99,"payload":{"contentType":"SECTION_SUMMARY","paperId":1,"sectionSummaryId":201,"text":"generic renewable energy background"}},
                  {"score":0.80,"payload":{"contentType":"SECTION_SUMMARY","paperId":1,"sectionSummaryId":202,"text":"photovoltaic PV power using LSTM"}}
                ]}
                """;
        when(qdrantService.searchSimilarChunksByContentTypesRaw(
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(6),
                org.mockito.ArgumentMatchers.eq(List.of()), org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(java.util.Map.of(
                        "PAPER_PROFILE", profiles,
                        "SECTION_SUMMARY", summaries,
                        "RAW_CHUNK", "{\"result\":[]}"
                ));
        when(paperReferenceMapper.selectById(1L)).thenReturn(paper(1L, "PV Paper"));
        when(paperReferenceMapper.selectById(2L)).thenReturn(paper(2L, "Wind Paper"));

        List<RagSource> sources = service.retrieveSourcesWithRewrite(rewrite, 2, List.of());

        assertThat(sources).extracting(RagSource::getPaperId).containsOnly(1L);
        assertThat(sources).extracting(RagSource::getSourceType)
                .containsExactlyInAnyOrder("paper_profile", "section_summary");
        assertThat(sources).filteredOn(source -> "section_summary".equals(source.getSourceType()))
                .singleElement()
                .extracting(RagSource::getContent)
                .isEqualTo("photovoltaic PV power using LSTM");
    }

    @Test
    void uniqueQuestionPrioritizesExplicitTermCoverageOverGenericTopScore() {
        List<PaperCandidate> candidates = List.of(
                new PaperCandidate(19L, 1.20, 3, 0.25),
                new PaperCandidate(38L, 1.05, 2, 1.00)
        );

        List<Long> selected = service.selectCandidatePaperIds(
                "文献库中唯一哪篇论文研究光伏功率预测？", candidates, 6);

        assertThat(selected).containsExactly(38L);
    }

    @Test
    void libraryDiscoveryPrefersDirectMethodEvidenceOverRelatedWorkSummary() {
        RagSource profile = source(30L, "paper_profile", 101L, null,
                "CLCRN profile", 0.9);
        RagSource background = source(30L, "section_summary", null, 201L,
                "本节综述了现有图神经网络、图结构、节点和邻接矩阵方法的局限。", 0.95);
        background.setSectionTitle("existing graph neural network methods");
        RagSource directMethod = source(30L, "section_summary", null, 202L,
                "本文通过k近邻算法构建图结构并生成邻接矩阵，用于建模站点空间关系。", 0.70);
        directMethod.setSectionTitle("MLP");

        List<RagSource> selected = service.selectSourcesByPaperRoundRobin(
                List.of(profile, background, directMethod),
                List.of(30L),
                List.of("图结构", "节点", "邻接矩阵", "近邻"),
                2);

        assertThat(selected).extracting(RagSource::getContent)
                .containsExactly("CLCRN profile", directMethod.getContent());
    }

    private RagSource source(Long paperId,
                             String sourceType,
                             Long profileId,
                             Long sectionSummaryId,
                             String content,
                             double score) {
        RagSource source = new RagSource();
        source.setPaperId(paperId);
        source.setSourceType(sourceType);
        source.setProfileId(profileId);
        source.setSectionSummaryId(sectionSummaryId);
        source.setContent(content);
        source.setScore(score);
        return source;
    }

    private PaperChunk chunk(Long id, Long paperId, int chunkIndex, String content) {
        PaperChunk c = new PaperChunk();
        c.setId(id);
        c.setPaperId(paperId);
        c.setChunkIndex(chunkIndex);
        c.setContent(content);
        return c;
    }

    private PaperReference paper(Long id, String title) {
        PaperReference p = new PaperReference();
        p.setId(id);
        p.setTitle(title);
        return p;
    }
}
