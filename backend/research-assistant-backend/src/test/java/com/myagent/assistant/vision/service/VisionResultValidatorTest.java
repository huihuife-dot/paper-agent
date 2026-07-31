package com.myagent.assistant.vision.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VisionResultValidatorTest {
    private final VisionResultValidator validator = new VisionResultValidator(new ObjectMapper());

    @Test
    void acceptsRequiredSchemaAndRemovesMarkdownFence() {
        String result = validator.validateAndNormalize("""
                ```json
                {"purpose":"architecture","components":[],"connections":[],"implementationFacts":[],"uncertainClaims":[]}
                ```
                """);
        assertThat(result).contains("\"purpose\":\"architecture\"").doesNotContain("```");
    }

    @Test
    void normalizesProviderShapeAndMarksMissingSafetyAssessment() {
        String result = validator.validateAndNormalize("""
                {"purpose":"table summary","components":[],"connections":"no explicit link",
                 "implementationFacts":[],"uncertainClaims":[]}
                """);
        assertThat(result).contains("\"connections\":[\"no explicit link\"]");

        String missingSafety = validator.validateAndNormalize("""
                {"purpose":"architecture","components":[],"connections":[]}
                """);
        assertThat(missingSafety).contains("manual review required");
    }

    @Test
    void rejectsNonJsonResponse() {
        assertThatThrownBy(() -> validator.validateAndNormalize("looks like a model diagram"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("合法 JSON");
    }
}
