package com.myagent.assistant.qdrant.service;

import com.myagent.assistant.embedding.EmbeddingService;
import com.myagent.assistant.paper.entity.PaperChunk;
import com.myagent.assistant.paper.entity.PaperReproductionFact;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QdrantServiceTest {

    @Test
    @SuppressWarnings("unchecked")
    void buildPointUsesIndexTextAndStructuredPayload() {
        EmbeddingService embeddingService = mock(EmbeddingService.class);
        when(embeddingService.embed("index text for retrieval")).thenReturn(List.of(0.1, 0.2, 0.3));

        QdrantService service = new QdrantService(embeddingService);

        PaperChunk chunk = new PaperChunk();
        chunk.setId(100L);
        chunk.setSectionId(7L);
        chunk.setSectionType("METHOD");
        chunk.setSectionTitle("Proposed Method");
        chunk.setChunkIndex(2);
        chunk.setContent("real chunk content");
        chunk.setIndexText("index text for retrieval");
        chunk.setIsReference(false);
        chunk.setIsNoise(false);
        chunk.setChunkStrategyVersion("paper-structure-v1");

        Map<String, Object> point = service.buildPoint(3L, chunk);
        Map<String, Object> payload = (Map<String, Object>) point.get("payload");

        assertThat(point.get("vector")).isEqualTo(List.of(0.1, 0.2, 0.3));
        assertThat(payload.get("paperId")).isEqualTo(3L);
        assertThat(payload.get("chunkId")).isEqualTo(100L);
        assertThat(payload.get("sectionId")).isEqualTo(7L);
        assertThat(payload.get("sectionType")).isEqualTo("METHOD");
        assertThat(payload.get("sectionTitle")).isEqualTo("Proposed Method");
        assertThat(payload.get("isReference")).isEqualTo(false);
        assertThat(payload.get("isNoise")).isEqualTo(false);
        assertThat(payload.get("contentType")).isEqualTo("RAW_CHUNK");
        assertThat(payload.get("chunkStrategyVersion")).isEqualTo("paper-structure-v1");
    }

    @Test
    @SuppressWarnings("unchecked")
    void buildDeletePaperPointsBodyFiltersByPaperId() {
        EmbeddingService embeddingService = mock(EmbeddingService.class);
        QdrantService service = new QdrantService(embeddingService);

        Map<String, Object> body = service.buildDeletePaperPointsBody(12L);
        Map<String, Object> filter = (Map<String, Object>) body.get("filter");
        List<Map<String, Object>> must = (List<Map<String, Object>>) filter.get("must");
        Map<String, Object> condition = must.get(0);
        Map<String, Object> match = (Map<String, Object>) condition.get("match");

        assertThat(condition.get("key")).isEqualTo("paperId");
        assertThat(match.get("value")).isEqualTo(12L);
    }

    @Test
    void deletePaperPointsRejectsInvalidPaperIdBeforeCallingQdrant() {
        EmbeddingService embeddingService = mock(EmbeddingService.class);
        QdrantService service = new QdrantService(embeddingService);

        Map<String, Object> result = service.deletePaperPoints(0L);

        assertThat(result.get("success")).isEqualTo(false);
        assertThat(result.get("collection")).isEqualTo("paper_chunks");
        assertThat(result.get("error")).isEqualTo("文献ID不能为空");
    }

    @Test
    @SuppressWarnings("unchecked")
    void reproductionFactPointUsesIndependentTraceablePayload() {
        EmbeddingService embeddingService = mock(EmbeddingService.class);
        when(embeddingService.embed(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(List.of(0.1, 0.2, 0.3));
        QdrantService service = new QdrantService(embeddingService);
        PaperReproductionFact fact = new PaperReproductionFact();
        fact.setId(21L);
        fact.setPaperId(38L);
        fact.setFactType("HYPERPARAMETER");
        fact.setFactKey("batch_size");
        fact.setFactValue("32");
        fact.setSourceKind("TABLE");
        fact.setSourceId(9L);
        fact.setPageNumber(4);
        fact.setEvidenceExcerpt("Batch size is 32");
        fact.setVerificationStatus("USER_CONFIRMED");

        Map<String, Object> point = service.buildReproductionFactPoint(fact);
        Map<String, Object> payload = (Map<String, Object>) point.get("payload");

        assertThat(point.get("id")).isEqualTo(fact.getQdrantPointId());
        assertThat(payload).containsEntry("contentType", "REPRODUCTION_FACT")
                .containsEntry("paperId", 38L)
                .containsEntry("factId", 21L)
                .containsEntry("sourceKind", "TABLE")
                .containsEntry("sourceId", 9L)
                .containsEntry("pageNumber", 4);
    }
}
