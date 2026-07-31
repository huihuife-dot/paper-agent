package com.myagent.assistant.paper.reproduction;

import com.myagent.assistant.paper.entity.PaperAsset;
import com.myagent.assistant.paper.entity.PaperChunk;
import com.myagent.assistant.paper.entity.PaperReproductionFact;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReproductionFactExtractorTest {
    private final ReproductionFactExtractor extractor = new ReproductionFactExtractor();

    @Test
    void visualSchemaCreatesAtomicFactsButExcludesUncertainClaims() {
        PaperAsset asset = asset("FIGURE", "MODEL_INFERRED");
        asset.setSemanticDescription("""
                {"purpose":"Explain the encoder pipeline.",
                 "components":["Encoder","Projection head"],
                 "connections":["Encoder feeds projection head"],
                 "implementationFacts":["The projection has two layers"],
                 "uncertainClaims":["The hidden size may be 256"]}
                """);

        List<PaperReproductionFact> facts = extractor.fromAsset(asset);

        assertThat(facts).extracting(PaperReproductionFact::getFactType)
                .containsExactly("ARCHITECTURE", "ARCHITECTURE_COMPONENT",
                        "ARCHITECTURE_COMPONENT", "ARCHITECTURE_CONNECTION", "IMPLEMENTATION");
        assertThat(facts).allMatch(fact -> fact.getSourceId().equals(9L)
                && fact.getPageNumber().equals(4)
                && "MODEL_INFERRED".equals(fact.getVerificationStatus()));
        assertThat(facts).noneMatch(fact -> fact.getFactValue().contains("256"));
    }

    @Test
    void equationFactRetainsAssetPageAndEvidence() {
        PaperAsset asset = asset("EQUATION", "USER_CONFIRMED");
        asset.setStructuredContentJson("""
                {"expression":"L = L_task + 0.1 L_reg",
                 "nearbyText":"The training objective uses loss L = L_task + 0.1 L_reg."}
                """);

        List<PaperReproductionFact> facts = extractor.fromAsset(asset);

        assertThat(facts).hasSize(1);
        assertThat(facts.get(0).getFactType()).isEqualTo("LOSS");
        assertThat(facts.get(0).getFactKey()).isEqualTo("Figure 1@p4/Figure 1");
        assertThat(facts.get(0).getFactValue()).isEqualTo("L = L_task + 0.1 L_reg");
        assertThat(facts.get(0).getConfidence()).isEqualTo(0.55);
        assertThat(facts.get(0).getVerificationStatus()).isEqualTo("USER_CONFIRMED");
    }

    @Test
    void figureCaptionContextBecomesTraceableEvidenceWithoutModelInference() {
        PaperAsset asset = asset("FIGURE", "EXTRACTED");
        asset.setRawText("Fig. 3. The encoder uses ERA5 inputs and produces 48-hour forecasts.");

        List<PaperReproductionFact> facts = extractor.fromAsset(asset);

        assertThat(facts).hasSize(1);
        assertThat(facts.get(0).getFactType()).isEqualTo("EVIDENCE_CONTEXT");
        assertThat(facts.get(0).getSourceKind()).isEqualTo("FIGURE");
        assertThat(facts.get(0).getPageNumber()).isEqualTo(4);
        assertThat(facts.get(0).getEvidenceExcerpt()).contains("ERA5", "48-hour");
    }

    @Test
    void methodChunkExtractsOnlyExplicitParameterValues() {
        PaperChunk chunk = new PaperChunk();
        chunk.setId(17L);
        chunk.setPaperId(38L);
        chunk.setSectionType("METHOD");
        chunk.setPageStart(6);
        chunk.setContent("We used a learning rate of 0.001 and batch size 32. Optimizer details were omitted.");
        chunk.setIsReference(false);
        chunk.setIsNoise(false);
        chunk.setChunkStrategyVersion("paper-structure-v1");

        List<PaperReproductionFact> facts = extractor.fromChunk(chunk);

        assertThat(facts).extracting(PaperReproductionFact::getFactKey)
                .containsExactlyInAnyOrder("learning_rate", "batch_size");
        assertThat(facts).extracting(PaperReproductionFact::getFactValue)
                .containsExactlyInAnyOrder("0.001", "32");
        assertThat(facts).allMatch(fact -> "RAW_CHUNK".equals(fact.getSourceKind()));
        assertThat(facts).allMatch(fact -> fact.getConditionsJson().contains("\"sectionType\":\"METHOD\""));
        assertThat(facts.stream().filter(fact -> "batch_size".equals(fact.getFactKey())).findFirst().orElseThrow().getUnit())
                .isEqualTo("samples");
    }

    private PaperAsset asset(String type, String status) {
        PaperAsset asset = new PaperAsset();
        asset.setId(9L);
        asset.setPaperId(38L);
        asset.setAssetType(type);
        asset.setAssetLabel("Figure 1");
        asset.setPageStart(4);
        asset.setExtractionConfidence(0.55);
        asset.setVerificationStatus(status);
        asset.setSourceRevision("rev-1");
        return asset;
    }
}
