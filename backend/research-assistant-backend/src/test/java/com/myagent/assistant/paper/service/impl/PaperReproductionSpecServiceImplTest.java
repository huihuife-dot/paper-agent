package com.myagent.assistant.paper.service.impl;

import com.myagent.assistant.paper.entity.PaperReference;
import com.myagent.assistant.paper.entity.PaperReproductionFact;
import com.myagent.assistant.paper.entity.PaperReproductionSpec;
import com.myagent.assistant.paper.mapper.PaperReferenceMapper;
import com.myagent.assistant.paper.mapper.PaperReproductionFactMapper;
import com.myagent.assistant.paper.mapper.PaperReproductionSpecMapper;
import com.myagent.assistant.paper.reproduction.ReproductionSpecDocument;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaperReproductionSpecServiceImplTest {
    @Test
    void separatesTrustedReviewInferredAndConflictingFacts() {
        PaperReferenceMapper paperMapper = mock(PaperReferenceMapper.class);
        PaperReproductionFactMapper factMapper = mock(PaperReproductionFactMapper.class);
        PaperReproductionSpecMapper specMapper = mock(PaperReproductionSpecMapper.class);
        when(paperMapper.selectById(38L)).thenReturn(new PaperReference());
        when(specMapper.selectOne(any())).thenReturn(null);
        when(factMapper.selectList(any())).thenReturn(List.of(
                fact(1L, "ARCHITECTURE", "model", "LSTM", 0.9, "EXTRACTED", null),
                fact(2L, "HYPERPARAMETER", "batch_size", "32", 0.5, "EXTRACTED", null),
                fact(3L, "IMPLEMENTATION", "gate", "three gates", 0.6, "MODEL_INFERRED", null),
                fact(4L, "HYPERPARAMETER", "learning_rate", "0.001", 0.9, "EXTRACTED", "conflict-a"),
                fact(5L, "HYPERPARAMETER", "learning_rate", "0.01", 0.9, "EXTRACTED", "conflict-a")));
        PaperReproductionSpecServiceImpl service =
                new PaperReproductionSpecServiceImpl(paperMapper, factMapper, specMapper);

        ReproductionSpecDocument spec = service.rebuild(38L);

        assertThat(spec.getTrustedFacts()).extracting(ReproductionSpecDocument.FactItem::getFactId)
                .containsExactly(1L);
        assertThat(spec.getReviewRequiredFacts()).extracting(ReproductionSpecDocument.FactItem::getFactId)
                .containsExactly(2L);
        assertThat(spec.getModelInferredFacts()).extracting(ReproductionSpecDocument.FactItem::getFactId)
                .containsExactly(3L);
        assertThat(spec.getConflicts()).hasSize(1);
        assertThat(spec.getConflicts().get(0).getCandidates()).hasSize(2);
        assertThat(spec.getStatus()).isEqualTo("NEEDS_REVIEW");
        assertThat(spec.getProvenanceCoverage()).isEqualTo(1.0);
        assertThat(spec.getMissingInformation()).contains("hyperparameters: no trusted evidence");
        ArgumentCaptor<PaperReproductionSpec> saved = ArgumentCaptor.forClass(PaperReproductionSpec.class);
        verify(specMapper).insert(saved.capture());
        assertThat(saved.getValue().getSpecJson()).contains("\"reviewRequiredFacts\"");
    }

    private PaperReproductionFact fact(Long id, String type, String key, String value, double confidence,
                                       String status, String conflict) {
        PaperReproductionFact fact = new PaperReproductionFact();
        fact.setId(id);
        fact.setPaperId(38L);
        fact.setFactType(type);
        fact.setFactKey(key);
        fact.setFactValue(value);
        fact.setSourceKind("TABLE");
        fact.setSourceId(20L + id);
        fact.setPageNumber(4);
        fact.setEvidenceExcerpt(key + "=" + value);
        fact.setConfidence(confidence);
        fact.setVerificationStatus(status);
        fact.setConflictGroup(conflict);
        fact.setExtractorVersion("fact-v1");
        return fact;
    }
}
