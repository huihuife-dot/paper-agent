package com.myagent.assistant.paper.structure;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PaperTextCleanerTest {

    @Test
    void cleanNormalizesLineBreaksAndBlankLines() {
        PaperTextCleaner cleaner = new PaperTextCleaner();

        String cleaned = cleaner.clean("Abstract\r\n\r\n\r\nThis   is   a paper.\r\n\r\nMethod");

        assertThat(cleaned).isEqualTo("Abstract\n\nThis is a paper.\n\nMethod");
    }

    @Test
    void cleanRepairsEnglishHyphenLineBreaks() {
        PaperTextCleaner cleaner = new PaperTextCleaner();

        String cleaned = cleaner.clean("wind predic-\ntion model");

        assertThat(cleaned).isEqualTo("wind prediction model");
    }

    @Test
    void cleanRemovesStandalonePageNumbers() {
        PaperTextCleaner cleaner = new PaperTextCleaner();

        String cleaned = cleaner.clean("Introduction\n1\nThis paper studies wind prediction.\n23\nConclusion");

        assertThat(cleaned).doesNotContain("\n1\n");
        assertThat(cleaned).doesNotContain("\n23\n");
        assertThat(cleaned).contains("Introduction");
        assertThat(cleaned).contains("Conclusion");
    }
}
