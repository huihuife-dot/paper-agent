package com.myagent.assistant.vision.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

@Component
public class VisionResultValidator {
    private final ObjectMapper objectMapper;

    public VisionResultValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String validateAndNormalize(String content) {
        if (content == null || content.isBlank()) {
            throw new RuntimeException("视觉模型返回为空");
        }
        String normalized = content.trim();
        if (normalized.startsWith("```")) {
            normalized = normalized.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "");
        }
        try {
            JsonNode parsed = objectMapper.readTree(normalized);
            if (!(parsed instanceof ObjectNode root)) {
                throw new RuntimeException("视觉模型结果必须是 JSON 对象");
            }
            requireText(root, "purpose");
            normalizeOptionalArray(root, "components");
            normalizeOptionalArray(root, "connections");
            normalizeOptionalArray(root, "implementationFacts");
            normalizeSafetyArray(root);
            return objectMapper.writeValueAsString(root);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("视觉模型结果不是合法 JSON", e);
        }
    }

    private void requireText(JsonNode root, String field) {
        if (!root.path(field).isTextual()) {
            throw new RuntimeException("视觉模型结果缺少字段：" + field);
        }
    }

    private void requireArray(JsonNode root, String field) {
        if (!root.path(field).isArray()) {
            throw new RuntimeException("视觉模型结果缺少数组字段：" + field);
        }
    }

    private void normalizeOptionalArray(ObjectNode root, String field) {
        JsonNode value = root.get(field);
        if (value == null || value.isNull()) {
            root.putArray(field);
            return;
        }
        if (!value.isArray()) {
            // Some providers return one descriptive item as a string/object.
            // Preserve it as a single list item instead of failing the whole asset.
            String item = value.isTextual() ? value.asText() : value.toString();
            root.putArray(field).add(item);
        }
    }

    private void normalizeSafetyArray(ObjectNode root) {
        JsonNode value = root.get("uncertainClaims");
        if (value == null || value.isNull()) {
            root.putArray("uncertainClaims")
                    .add("Provider omitted uncertainty assessment; manual review required.");
            return;
        }
        if (!value.isArray()) {
            String item = value.isTextual() ? value.asText() : value.toString();
            root.putArray("uncertainClaims").add(item);
        }
    }
}
