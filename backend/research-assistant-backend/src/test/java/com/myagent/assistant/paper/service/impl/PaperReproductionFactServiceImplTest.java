package com.myagent.assistant.paper.service.impl;

import com.myagent.assistant.paper.entity.PaperReproductionFact;
import com.myagent.assistant.paper.mapper.PaperAssetMapper;
import com.myagent.assistant.paper.mapper.PaperChunkMapper;
import com.myagent.assistant.paper.mapper.PaperReferenceMapper;
import com.myagent.assistant.paper.mapper.PaperReproductionFactMapper;
import com.myagent.assistant.paper.reproduction.ReproductionFactExtractor;
import com.myagent.assistant.qdrant.service.QdrantService;
import com.myagent.assistant.paper.service.PaperReproductionSpecService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class PaperReproductionFactServiceImplTest {
    private final PaperReproductionFactServiceImpl service = new PaperReproductionFactServiceImpl(
            mock(PaperReferenceMapper.class), mock(PaperAssetMapper.class), mock(PaperChunkMapper.class),
            mock(PaperReproductionFactMapper.class), new ReproductionFactExtractor(), mock(QdrantService.class),
            mock(PaperReproductionSpecService.class));

    @Test
    void exactFactsMergeAndRetainAllSupportingSources() {
        PaperReproductionFact first = fact("batch_size", "32", 1L, 0.8);
        PaperReproductionFact second = fact("batch_size", "32", 2L, 0.95);

        List<PaperReproductionFact> merged = service.mergeAndMarkConflicts(List.of(first, second));

        assertThat(merged).hasSize(1);
        assertThat(merged.get(0).getSourceId()).isEqualTo(2L);
        assertThat(merged.get(0).getSupportingSourcesJson())
                .contains("\"sourceId\":1", "\"sourceId\":2");
        assertThat(merged.get(0).getConflictGroup()).isNull();
    }

    @Test
    void differentValuesForSameLogicalFactAreExposedAsConflict() {
        List<PaperReproductionFact> merged = service.mergeAndMarkConflicts(List.of(
                fact("learning_rate", "0.001", 1L, 0.9),
                fact("learning_rate", "0.01", 2L, 0.9)));

        assertThat(merged).hasSize(2);
        assertThat(merged).allMatch(fact -> fact.getConflictGroup() != null);
        assertThat(merged).extracting(PaperReproductionFact::getConflictGroup).doesNotContainNull();
        assertThat(merged.get(0).getConflictGroup()).isEqualTo(merged.get(1).getConflictGroup());
    }

    private PaperReproductionFact fact(String key, String value, Long sourceId, double confidence) {
        PaperReproductionFact fact = new PaperReproductionFact();
        fact.setPaperId(38L);
        fact.setFactType("HYPERPARAMETER");
        fact.setFactKey(key);
        fact.setFactValue(value);
        fact.setSourceKind("RAW_CHUNK");
        fact.setSourceId(sourceId);
        fact.setPageNumber(4);
        fact.setEvidenceExcerpt(key + "=" + value);
        fact.setConfidence(confidence);
        fact.setVerificationStatus("EXTRACTED");
        return fact;
    }
}
