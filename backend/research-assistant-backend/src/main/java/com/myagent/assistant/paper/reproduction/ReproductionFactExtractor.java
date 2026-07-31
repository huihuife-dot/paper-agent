package com.myagent.assistant.paper.reproduction;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myagent.assistant.paper.entity.PaperAsset;
import com.myagent.assistant.paper.entity.PaperChunk;
import com.myagent.assistant.paper.entity.PaperReproductionFact;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 将来源内容拆成小而可追溯的事实。这里只做保守的确定性规则，
 * 不使用第二个生成模型补写论文没有明确给出的参数。
 */
@Component
public class ReproductionFactExtractor {
    public static final String VERSION = "reproduction-fact-v2";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern NUMBERED_STEP = Pattern.compile(
            "(?s)(?:^|\\s)\\((\\d{1,2})\\)\\s*(.*?)(?=(?:\\s\\(\\d{1,2}\\)\\s)|$)");
    private static final Map<String, Pattern> PARAMETER_PATTERNS = Map.of(
            "learning_rate", Pattern.compile("(?i)learning\\s+rate\\s*(?:of|=|:|was|is)?\\s*([0-9]+(?:\\.[0-9]+)?(?:e[-+]?\\d+)?)"),
            "batch_size", Pattern.compile("(?i)batch\\s+size\\s*(?:of|=|:|was|is)?\\s*(\\d+)"),
            "epochs", Pattern.compile("(?i)(?:number\\s+of\\s+)?epochs?\\s*(?:of|=|:|was|is)?\\s*(\\d+)"),
            "dropout", Pattern.compile("(?i)dropout(?:\\s+rate)?\\s*(?:of|=|:|was|is)?\\s*([0-9]+(?:\\.[0-9]+)?)"),
            "window_size", Pattern.compile("(?i)(?:window|sequence)\\s+(?:size|length)\\s*(?:of|=|:|was|is)?\\s*(\\d+)"));

    public List<PaperReproductionFact> fromAsset(PaperAsset asset) {
        List<PaperReproductionFact> facts = new ArrayList<>();
        if (asset == null || "REJECTED".equals(asset.getVerificationStatus())
                || "PAGE_IMAGE".equals(asset.getAssetType())) {
            return facts;
        }
        extractStructuredAsset(asset, facts);
        extractSourceContext(asset, facts);
        extractSemanticAsset(asset, facts);
        return facts;
    }

    public List<PaperReproductionFact> fromChunk(PaperChunk chunk) {
        List<PaperReproductionFact> facts = new ArrayList<>();
        if (chunk == null || Boolean.TRUE.equals(chunk.getIsReference()) || Boolean.TRUE.equals(chunk.getIsNoise())
                || chunk.getContent() == null || chunk.getContent().isBlank()) {
            return facts;
        }
        String section = String.valueOf(chunk.getSectionType()).toUpperCase(Locale.ROOT);
        if (!(section.contains("METHOD") || section.contains("EXPERIMENT")
                || section.contains("IMPLEMENT") || section.contains("MODEL"))) {
            return facts;
        }
        for (Map.Entry<String, Pattern> entry : PARAMETER_PATTERNS.entrySet()) {
            Matcher matcher = entry.getValue().matcher(chunk.getContent());
            while (matcher.find()) {
                PaperReproductionFact fact = fact(chunk.getPaperId(), "HYPERPARAMETER", entry.getKey(), matcher.group(1),
                        "RAW_CHUNK", chunk.getId(), page(chunk), excerpt(chunk.getContent(), matcher.start(), matcher.end()),
                        0.82, "EXTRACTED", chunk.getChunkStrategyVersion());
                fact.setUnit(parameterUnit(entry.getKey()));
                fact.setConditionsJson(json(Map.of(
                        "sectionType", firstNonBlank(chunk.getSectionType(), "UNKNOWN"),
                        "sectionTitle", firstNonBlank(chunk.getSectionTitle(), ""))));
                facts.add(fact);
            }
        }
        return facts;
    }

    private void extractStructuredAsset(PaperAsset asset, List<PaperReproductionFact> facts) {
        JsonNode structured = parse(asset.getStructuredContentJson());
        if (structured == null) {
            return;
        }
        String type = asset.getAssetType();
        if ("EQUATION".equals(type)) {
            String expression = text(structured, "expression");
            if (!expression.isBlank()) {
                String nearby = text(structured, "nearbyText");
                String factType = containsAny(nearby, "loss", "objective") ? "LOSS" : "EQUATION";
                facts.add(assetFact(asset, factType, label(asset, "equation"), expression,
                        nearby.isBlank() ? expression : nearby, asset.getExtractionConfidence()));
            }
        } else if ("ALGORITHM".equals(type)) {
            String steps = text(structured, "rawSteps");
            Matcher matcher = NUMBERED_STEP.matcher(steps);
            while (matcher.find()) {
                facts.add(assetFact(asset, "ALGORITHM_STEP", "step_" + matcher.group(1),
                        normalize(matcher.group(2)), matcher.group(0), asset.getExtractionConfidence()));
            }
        } else if ("TABLE".equals(type)) {
            String source = firstNonBlank(text(structured, "markdown"), text(structured, "rawRows"),
                    asset.getRawText());
            if (!source.isBlank() && containsAny(source + " " + asset.getCaption(),
                    "parameter", "configuration", "setting", "hyperparameter")) {
                facts.add(assetFact(asset, "HYPERPARAMETER_TABLE", label(asset, "table"),
                        source, firstNonBlank(asset.getCaption(), source), asset.getExtractionConfidence()));
            }
        }
    }

    private void extractSemanticAsset(PaperAsset asset, List<PaperReproductionFact> facts) {
        JsonNode semantic = parse(asset.getSemanticDescription());
        if (semantic == null) {
            return;
        }
        String purpose = text(semantic, "purpose");
        if (!purpose.isBlank()) {
            facts.add(assetFact(asset, "FIGURE".equals(asset.getAssetType()) ? "ARCHITECTURE" : "SEMANTIC_PURPOSE",
                    label(asset, "purpose"), purpose, purpose, semanticConfidence(asset)));
        }
        addArrayFacts(asset, facts, semantic.path("components"), "ARCHITECTURE_COMPONENT", "component");
        addArrayFacts(asset, facts, semantic.path("connections"), "ARCHITECTURE_CONNECTION", "connection");
        addArrayFacts(asset, facts, semantic.path("implementationFacts"), "IMPLEMENTATION", "implementation");
        // uncertainClaims 故意不进入事实库：它们只能在后续缺口分析中展示。
    }

    /**
     * Keep the deterministic caption/table context as traceable evidence. This is
     * deliberately typed as evidence context rather than an atomic implementation
     * claim, so downstream agents can quote it without treating every token as a
     * normalized paper fact.
     */
    private void extractSourceContext(PaperAsset asset, List<PaperReproductionFact> facts) {
        if (!List.of("FIGURE", "TABLE", "ALGORITHM").contains(asset.getAssetType())
                || asset.getRawText() == null || asset.getRawText().isBlank()) {
            return;
        }
        facts.add(assetFact(asset, "EVIDENCE_CONTEXT", "source_context",
                asset.getRawText(), asset.getRawText(), asset.getExtractionConfidence()));
    }

    private void addArrayFacts(PaperAsset asset, List<PaperReproductionFact> facts, JsonNode values,
                               String factType, String keyPrefix) {
        if (!values.isArray()) {
            return;
        }
        int index = 1;
        for (JsonNode value : values) {
            String text = value.asText("").trim();
            if (!text.isBlank()) {
                facts.add(assetFact(asset, factType, keyPrefix + "_" + index, text, text,
                        semanticConfidence(asset)));
                index++;
            }
        }
    }

    private PaperReproductionFact assetFact(PaperAsset asset, String type, String key, String value,
                                             String evidence, Double confidence) {
        String scopedKey = label(asset, asset.getAssetType().toLowerCase(Locale.ROOT))
                + "@p" + (asset.getPageStart() == null ? "unknown" : asset.getPageStart()) + "/" + key;
        PaperReproductionFact fact = fact(asset.getPaperId(), type, scopedKey, normalize(value), asset.getAssetType(), asset.getId(),
                asset.getPageStart(), evidence, confidence, asset.getVerificationStatus(), asset.getSourceRevision());
        fact.setConditionsJson(json(Map.of(
                "assetLabel", label(asset, asset.getAssetType()),
                "caption", firstNonBlank(asset.getCaption(), ""))));
        return fact;
    }

    private PaperReproductionFact fact(Long paperId, String type, String key, String value,
                                       String sourceKind, Long sourceId, Integer page, String evidence,
                                       Double confidence, String status, String sourceRevision) {
        PaperReproductionFact fact = new PaperReproductionFact();
        fact.setPaperId(paperId);
        fact.setFactType(type);
        fact.setFactKey(normalize(key));
        fact.setFactValue(normalize(value));
        fact.setSourceKind(sourceKind);
        fact.setSourceId(sourceId);
        fact.setPageNumber(page);
        fact.setEvidenceExcerpt(truncate(normalize(evidence), 1000));
        fact.setConfidence(clamp(confidence));
        fact.setVerificationStatus(firstNonBlank(status, "EXTRACTED"));
        fact.setExtractorVersion(VERSION);
        fact.setSourceRevision(sourceRevision);
        return fact;
    }

    private JsonNode parse(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return JSON.readTree(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String text(JsonNode node, String field) {
        return node.path(field).asText("").trim();
    }

    private String label(PaperAsset asset, String fallback) {
        return firstNonBlank(asset.getAssetLabel(), fallback);
    }

    private Integer page(PaperChunk chunk) {
        return chunk.getPageNumber() != null ? chunk.getPageNumber() : chunk.getPageStart();
    }

    private String excerpt(String text, int start, int end) {
        return text.substring(Math.max(0, start - 100), Math.min(text.length(), end + 160));
    }

    private boolean containsAny(String value, String... needles) {
        String lower = String.valueOf(value).toLowerCase(Locale.ROOT);
        for (String needle : needles) if (lower.contains(needle)) return true;
        return false;
    }

    private double semanticConfidence(PaperAsset asset) {
        if ("USER_CONFIRMED".equals(asset.getVerificationStatus())) return 1.0;
        if ("CROSS_CHECKED".equals(asset.getVerificationStatus())) return 0.95;
        return Math.min(0.60, clamp(asset.getExtractionConfidence()));
    }

    private double clamp(Double value) {
        if (value == null) return 0.5;
        return Math.max(0.0, Math.min(1.0, value));
    }

    private String normalize(String value) {
        return String.valueOf(value == null ? "" : value).replaceAll("\\s+", " ").trim();
    }

    private String truncate(String value, int length) {
        return value.length() <= length ? value : value.substring(0, length);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value.trim();
        return "";
    }

    private String parameterUnit(String key) {
        return switch (key) {
            case "batch_size" -> "samples";
            case "epochs" -> "epochs";
            case "window_size" -> "steps";
            default -> null;
        };
    }

    private String json(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
