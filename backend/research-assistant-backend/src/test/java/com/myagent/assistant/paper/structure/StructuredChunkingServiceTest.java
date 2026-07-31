package com.myagent.assistant.paper.structure;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StructuredChunkingServiceTest {

    @Test
    void chunkCreatesStructuredChunksBySection() {
        StructuredChunkingService service = new StructuredChunkingService(
                new PaperTextCleaner(),
                new PaperSectionDetector()
        );

        List<StructuredChunk> chunks = service.chunk("Wind Prediction Paper", """
                Abstract
                This paper studies wind prediction.

                Proposed Method
                We propose a dual branch neural network for wind prediction. The model captures temporal features.

                Experiments
                The method is evaluated on three datasets and outperforms baselines.
                """);

        assertThat(chunks).isNotEmpty();
        assertThat(chunks).extracting(StructuredChunk::getSectionType)
                .contains(SectionType.ABSTRACT, SectionType.METHOD, SectionType.EXPERIMENT);
        assertThat(chunks.get(0).getIndexText()).contains("Paper Title: Wind Prediction Paper");
        assertThat(chunks.get(0).getChunkStrategyVersion()).isEqualTo("paper-structure-v1");
    }

    @Test
    void chunkMarksReferenceChunks() {
        StructuredChunkingService service = new StructuredChunkingService(
                new PaperTextCleaner(),
                new PaperSectionDetector()
        );

        List<StructuredChunk> chunks = service.chunk("Paper", """
                Method
                Model content.

                References
                [1] Some referenced paper.
                """);

        assertThat(chunks).anySatisfy(chunk -> {
            assertThat(chunk.getSectionType()).isEqualTo(SectionType.REFERENCES);
            assertThat(chunk.getReference()).isTrue();
        });
    }

    @Test
    void chunkMarksBackMatterAsNoise() {
        StructuredChunkingService service = new StructuredChunkingService(
                new PaperTextCleaner(),
                new PaperSectionDetector()
        );

        List<StructuredChunk> chunks = service.chunk("Paper", """
                Conclusion
                This paper concludes the proposed method is effective.

                Author Contributions
                Alice wrote the manuscript. Bob reviewed the paper.
                """);

        assertThat(chunks).anySatisfy(chunk -> {
            assertThat(chunk.getSectionType()).isEqualTo(SectionType.BACK_MATTER);
            assertThat(chunk.getNoise()).isTrue();
        });
    }

    @Test
    void chunkSplitsLongSectionIntoMultipleChunks() {
        StructuredChunkingService service = new StructuredChunkingService(
                new PaperTextCleaner(),
                new PaperSectionDetector()
        );

        String repeated = "This method paragraph explains the proposed neural architecture and experimental design. ".repeat(80);
        List<StructuredChunk> chunks = service.chunk("Long Paper", "Method\n" + repeated);

        assertThat(chunks.size()).isGreaterThan(1);
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.getCharCount()).isLessThanOrEqualTo(2400));
    }
}
