package com.myagent.assistant.paper.structure;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PaperSectionDetectorTest {

    @Test
    void detectRecognizesCommonPaperSections() {
        PaperSectionDetector detector = new PaperSectionDetector();

        List<DetectedSection> sections = detector.detect("""
                Abstract
                This paper proposes a model.

                1 Introduction
                Wind prediction is important.

                2 Proposed Method
                We propose a dual branch network.

                3 Experiments
                We evaluate on several datasets.

                References
                [1] A referenced paper.
                """);

        assertThat(sections).extracting(DetectedSection::getType)
                .containsExactly(
                        SectionType.ABSTRACT,
                        SectionType.INTRODUCTION,
                        SectionType.METHOD,
                        SectionType.EXPERIMENT,
                        SectionType.REFERENCES
                );
    }

    @Test
    void detectUsesUnknownWhenNoHeadingExists() {
        PaperSectionDetector detector = new PaperSectionDetector();

        List<DetectedSection> sections = detector.detect("This is a plain parsed PDF text without headings.");

        assertThat(sections).hasSize(1);
        assertThat(sections.get(0).getType()).isEqualTo(SectionType.UNKNOWN);
        assertThat(sections.get(0).getTitle()).isEqualTo("Unknown");
    }

    @Test
    void detectMarksEverythingAfterReferencesAsReferences() {
        PaperSectionDetector detector = new PaperSectionDetector();

        List<DetectedSection> sections = detector.detect("""
                Method
                Model content.

                References
                [1] First reference.
                [2] Second reference.
                """);

        DetectedSection references = sections.get(1);
        assertThat(references.getType()).isEqualTo(SectionType.REFERENCES);
        assertThat(references.isReference()).isTrue();
        assertThat(references.getContent()).contains("[1] First reference");
    }

    @Test
    void detectInfersSectionTypeFromContentWhenTitleIsUnclear() {
        PaperSectionDetector detector = new PaperSectionDetector();

        List<DetectedSection> sections = detector.detect("""
                Our System
                We propose a neural architecture with two modules and a training procedure for wind prediction.
                """);

        assertThat(sections).hasSize(1);
        assertThat(sections.get(0).getTitle()).isEqualTo("Our System");
        assertThat(sections.get(0).getType()).isEqualTo(SectionType.METHOD);
    }

    @Test
    void detectMarksBackMatterAfterConclusionAsLowValue() {
        PaperSectionDetector detector = new PaperSectionDetector();

        List<DetectedSection> sections = detector.detect("""
                Conclusion
                This paper concludes the proposed method is effective.

                Author Contributions
                Alice wrote the manuscript. Bob reviewed the paper.

                Conflict of Interest
                The authors declare no conflict of interest.
                """);

        assertThat(sections).extracting(DetectedSection::getType)
                .containsExactly(SectionType.CONCLUSION, SectionType.BACK_MATTER, SectionType.BACK_MATTER);
        assertThat(sections.get(1).isLowValueBackMatter()).isTrue();
    }
}
