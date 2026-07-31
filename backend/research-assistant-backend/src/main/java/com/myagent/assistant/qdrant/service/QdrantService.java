package com.myagent.assistant.qdrant.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myagent.assistant.paper.entity.PaperChunk;
import com.myagent.assistant.paper.entity.PaperProfile;
import com.myagent.assistant.paper.entity.PaperSectionSummary;
import com.myagent.assistant.paper.entity.PaperReproductionFact;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import com.myagent.assistant.embedding.EmbeddingService;
import com.myagent.assistant.observability.RagTimingTrace;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Qdrant 服务类。
 * 负责 collection 创建、向量写入（chunk/profile/summary）和相似度检索。
 */
@Service
public class QdrantService {

    @Value("${app.qdrant.url}")
    private String qdrantUrl;

    private final EmbeddingService embeddingService;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String REPRODUCTION_FACT_COLLECTION = "paper_reproduction_facts";

    public QdrantService(EmbeddingService embeddingService) {
        this.embeddingService = embeddingService;
    }

    // ===== Health =====

    public Map<String, Object> health() {
        Map<String, Object> result = new HashMap<>();
        try {
            String response = RestClient.create()
                    .get()
                    .uri(qdrantUrl)
                    .retrieve()
                    .body(String.class);
            result.put("available", true);
            result.put("url", qdrantUrl);
            result.put("response", response);
        } catch (Exception e) {
            result.put("available", false);
            result.put("url", qdrantUrl);
            result.put("error", e.getMessage());
        }
        return result;
    }

    // ===== Collection =====

    public Map<String, Object> createPaperChunksCollection() {
        Map<String, Object> result = new HashMap<>();
        try {
            String url = qdrantUrl + "/collections/paper_chunks";
            Map<String, Object> body = Map.of(
                    "vectors", Map.of(
                            "size", embeddingService.dimension(),
                            "distance", "Cosine"
                    )
            );
            String response = RestClient.create()
                    .put()
                    .uri(url)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            result.put("success", true);
            result.put("collection", "paper_chunks");
            result.put("vectorSize", embeddingService.dimension());
            result.put("distance", "Cosine");
            result.put("response", response);
        } catch (Exception e) {
            result.put("success", false);
            result.put("collection", "paper_chunks");
            result.put("error", e.getMessage());
        }
        return result;
    }

    /**
     * 复现事实使用独立 collection，避免进入普通问答的固定候选池。
     */
    public void ensureReproductionFactCollection() {
        try {
            RestClient.create().get()
                    .uri(qdrantUrl + "/collections/" + REPRODUCTION_FACT_COLLECTION)
                    .retrieve().body(String.class);
            return;
        } catch (Exception ignored) {
            // Collection 不存在时再创建；普通 RAG collection 不受影响。
        }
        RestClient.create().put()
                .uri(qdrantUrl + "/collections/" + REPRODUCTION_FACT_COLLECTION)
                .body(Map.of("vectors", Map.of(
                        "size", embeddingService.dimension(),
                        "distance", "Cosine")))
                .retrieve().body(String.class);
    }

    public void upsertReproductionFacts(List<PaperReproductionFact> facts) {
        if (facts == null || facts.isEmpty()) {
            return;
        }
        ensureReproductionFactCollection();
        for (int start = 0; start < facts.size(); start += 10) {
            List<PaperReproductionFact> batch = facts.subList(start, Math.min(start + 10, facts.size()));
            List<String> texts = batch.stream().map(this::factEmbeddingText).toList();
            List<List<Double>> vectors = embeddingService.embedAll(texts);
            if (vectors.size() != batch.size()) {
                throw new RuntimeException("Embedding 批量返回数量不匹配");
            }
            List<Map<String, Object>> points = new ArrayList<>();
            for (int index = 0; index < batch.size(); index++) {
                points.add(buildReproductionFactPoint(batch.get(index), vectors.get(index)));
            }
            RestClient.create().put()
                    .uri(qdrantUrl + "/collections/" + REPRODUCTION_FACT_COLLECTION + "/points")
                    .body(Map.of("points", points))
                    .retrieve().body(String.class);
        }
    }

    Map<String, Object> buildReproductionFactPoint(PaperReproductionFact fact) {
        return buildReproductionFactPoint(fact, embeddingService.embed(factEmbeddingText(fact)));
    }

    private Map<String, Object> buildReproductionFactPoint(PaperReproductionFact fact, List<Double> vector) {
        String pointId = derivedPointId("REPRODUCTION_FACT", fact.getId());
        fact.setQdrantPointId(pointId);
        Map<String, Object> payload = new HashMap<>();
        payload.put("paperId", fact.getPaperId());
        payload.put("factId", fact.getId());
        payload.put("factType", fact.getFactType());
        payload.put("factKey", fact.getFactKey());
        payload.put("sourceKind", fact.getSourceKind());
        payload.put("sourceId", fact.getSourceId());
        payload.put("pageNumber", fact.getPageNumber());
        payload.put("verificationStatus", fact.getVerificationStatus());
        payload.put("contentType", "REPRODUCTION_FACT");
        payload.put("text", previewText(factEmbeddingText(fact)));
        return Map.of(
                "id", pointId,
                "vector", vector,
                "payload", payload);
    }

    public void deleteReproductionFactPoints(Long paperId) {
        if (paperId == null || paperId <= 0) {
            return;
        }
        try {
            RestClient.create().post()
                    .uri(qdrantUrl + "/collections/" + REPRODUCTION_FACT_COLLECTION + "/points/delete")
                    .body(buildDeletePaperPointsBody(paperId))
                    .retrieve().body(String.class);
        } catch (Exception e) {
            // Collection 尚未创建等同于没有可删除的事实点。
            if (!String.valueOf(e.getMessage()).contains("404")) {
                throw e;
            }
        }
    }

    public String searchReproductionFactsRaw(String question, Long paperId, Integer topK) {
        if (question == null || question.isBlank()) {
            throw new RuntimeException("检索问题不能为空");
        }
        ensureReproductionFactCollection();
        Map<String, Object> body = new HashMap<>();
        body.put("vector", embeddingService.embed(question));
        body.put("limit", topK != null && topK > 0 ? topK : 10);
        body.put("with_payload", true);
        if (paperId != null && paperId > 0) {
            body.put("filter", Map.of("must", List.of(Map.of(
                    "key", "paperId", "match", Map.of("value", paperId)))));
        }
        return RestClient.create().post()
                .uri(qdrantUrl + "/collections/" + REPRODUCTION_FACT_COLLECTION + "/points/search")
                .body(body)
                .retrieve().body(String.class);
    }

    // ===== Upsert: RAW_CHUNK =====

    public Map<String, Object> upsertPaperChunks(Long paperId, List<PaperChunk> chunks) {
        Map<String, Object> result = new HashMap<>();
        if (chunks == null || chunks.isEmpty()) {
            result.put("success", false);
            result.put("collection", "paper_chunks");
            result.put("error", "当前文献没有可向量化的 chunk，请先解析 PDF");
            return result;
        }
        try {
            String url = qdrantUrl + "/collections/paper_chunks/points";
            List<Map<String, Object>> points = chunks.stream()
                    .map(chunk -> buildPoint(paperId, chunk))
                    .toList();
            Map<String, Object> body = Map.of("points", points);
            String response = RestClient.create()
                    .put()
                    .uri(url)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            result.put("success", true);
            result.put("collection", "paper_chunks");
            result.put("paperId", paperId);
            result.put("vectorSize", embeddingService.dimension());
            result.put("chunkCount", chunks.size());
            result.put("response", response);
        } catch (Exception e) {
            result.put("success", false);
            result.put("collection", "paper_chunks");
            result.put("paperId", paperId);
            result.put("error", e.getMessage());
        }
        return result;
    }

    // ===== Upsert: PAPER_PROFILE =====

    /**
     * 将文献画像写入 Qdrant，供全库检索时使用。
     * 使用稳定 UUID，重复写入时覆盖同一个点，并避免与正数 chunk ID 冲突。
     */
    public void upsertProfilePoint(PaperProfile profile) {
        if (profile == null || profile.getPaperId() == null) {
            return;
        }
        String embeddingText = profile.getProfileText();
        if (embeddingText == null || embeddingText.isBlank()) {
            return;
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("paperId", profile.getPaperId());
        payload.put("profileId", profile.getId());
        payload.put("profileVersion", safeString(profile.getProfileVersion(), "paper-profile-v1"));
        payload.put("contentType", "PAPER_PROFILE");
        payload.put("text", previewText(embeddingText));

        Map<String, Object> point = Map.of(
                "id", derivedPointId("PAPER_PROFILE", profile.getId()),
                "vector", embeddingService.embed(embeddingText),
                "payload", payload
        );
        writePoints(point);
    }

    // ===== Upsert: SECTION_SUMMARY =====

    /**
     * 将章节摘要写入 Qdrant，供全库检索时使用。
     * 使用稳定 UUID，重复写入时覆盖同一个点，并避免与正数 chunk ID 冲突。
     */
    public void upsertSummaryPoint(PaperSectionSummary summary) {
        if (summary == null || summary.getId() == null) {
            return;
        }
        String embeddingText = summary.getSummary();
        if (embeddingText == null || embeddingText.isBlank()) {
            return;
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("paperId", summary.getPaperId());
        payload.put("sectionSummaryId", summary.getId());
        payload.put("sectionId", summary.getSectionId());
        payload.put("sectionType", safeString(summary.getSectionType(), "UNKNOWN"));
        payload.put("sectionTitle", safeString(summary.getSectionTitle(), ""));
        payload.put("summaryVersion", safeString(summary.getSummaryVersion(), "section-summary-v1"));
        payload.put("contentType", "SECTION_SUMMARY");
        payload.put("text", previewText(embeddingText));

        Map<String, Object> point = Map.of(
                "id", derivedPointId("SECTION_SUMMARY", summary.getId()),
                "vector", embeddingService.embed(embeddingText),
                "payload", payload
        );
        writePoints(point);
    }

    private void writePoints(Map<String, Object> point) {
        RestClient.create()
                .put()
                .uri(qdrantUrl + "/collections/paper_chunks/points")
                .body(Map.of("points", List.of(point)))
                .retrieve()
                .body(String.class);
    }

    public long countPaperPoints(Long paperId, String contentType) {
        try {
            Map<String, Object> body = Map.of(
                    "exact", true,
                    "filter", Map.of("must", List.of(
                            Map.of("key", "paperId", "match", Map.of("value", paperId)),
                            Map.of("key", "contentType", "match", Map.of("value", contentType))
                    ))
            );
            String response = RestClient.create()
                    .post()
                    .uri(qdrantUrl + "/collections/paper_chunks/points/count")
                    .body(body)
                    .retrieve()
                    .body(String.class);
            JsonNode count = OBJECT_MAPPER.readTree(response).path("result").path("count");
            return count.isNumber() ? count.asLong() : 0L;
        } catch (Exception e) {
            throw new RuntimeException("查询 Qdrant 索引状态失败：" + e.getMessage(), e);
        }
    }

    String derivedPointId(String contentType, Long databaseId) {
        if (databaseId == null) {
            throw new IllegalArgumentException("数据库ID不能为空");
        }
        return UUID.nameUUIDFromBytes((contentType + ":" + databaseId)
                .getBytes(StandardCharsets.UTF_8)).toString();
    }

    // ===== Delete =====

    /**
     * 删除某篇文献的所有向量点（chunk、profile、summary）。
     * 按 paperId 过滤，不需要依赖 MySQL 状态。
     */
    public Map<String, Object> deletePaperPoints(Long paperId) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", false);
        result.put("collection", "paper_chunks");
        result.put("paperId", paperId);
        if (paperId == null || paperId <= 0) {
            result.put("error", "文献ID不能为空");
            return result;
        }
        try {
            String url = qdrantUrl + "/collections/paper_chunks/points/delete";
            String response = RestClient.create()
                    .post()
                    .uri(url)
                    .body(buildDeletePaperPointsBody(paperId))
                    .retrieve()
                    .body(String.class);
            result.put("success", true);
            result.put("response", response);
        } catch (Exception e) {
            result.put("error", e.getMessage());
        }
        return result;
    }

    Map<String, Object> buildDeletePaperPointsBody(Long paperId) {
        return Map.of(
                "filter", Map.of(
                        "must", List.of(Map.of(
                                "key", "paperId",
                                "match", Map.of("value", paperId)
                        ))
                )
        );
    }

    // ===== Build points =====

    Map<String, Object> buildPoint(Long paperId, PaperChunk chunk) {
        return Map.of(
                "id", chunk.getId(),
                "vector", embeddingService.embed(embeddingText(chunk)),
                "payload", buildPayload(paperId, chunk)
        );
    }

    private Map<String, Object> buildPayload(Long paperId, PaperChunk chunk) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("paperId", paperId);
        payload.put("chunkId", chunk.getId());
        payload.put("sectionId", chunk.getSectionId());
        payload.put("sectionType", safeString(chunk.getSectionType(), "UNKNOWN"));
        payload.put("sectionTitle", safeString(chunk.getSectionTitle(), ""));
        payload.put("chunkIndex", chunk.getChunkIndex());
        payload.put("isReference", Boolean.TRUE.equals(chunk.getIsReference()));
        payload.put("isNoise", Boolean.TRUE.equals(chunk.getIsNoise()));
        payload.put("contentType", "RAW_CHUNK");
        payload.put("chunkStrategyVersion", safeString(chunk.getChunkStrategyVersion(), "fixed-window-v1"));
        payload.put("text", previewText(chunk.getContent()));
        return payload;
    }

    private String embeddingText(PaperChunk chunk) {
        if (chunk.getIndexText() != null && !chunk.getIndexText().isBlank()) {
            return chunk.getIndexText();
        }
        return chunk.getContent() != null ? chunk.getContent() : "";
    }

    private String factEmbeddingText(PaperReproductionFact fact) {
        return String.join("\n",
                safeString(fact.getFactType(), "FACT"),
                safeString(fact.getFactKey(), ""),
                safeString(fact.getFactValue(), ""),
                safeString(fact.getEvidenceExcerpt(), ""));
    }

    // ===== Search =====

    public Map<String, Object> searchSimilarChunks(String question, Integer topK) {
        Map<String, Object> result = new HashMap<>();
        if (question == null || question.isBlank()) {
            result.put("success", false);
            result.put("error", "问题不能为空");
            return result;
        }
        int limit = topK != null && topK > 0 ? topK : 5;
        try {
            List<Double> queryVector = embeddingService.embed(question);
            String response = searchSimilarChunksRaw(question, limit);
            result.put("success", true);
            result.put("collection", "paper_chunks");
            result.put("question", question);
            result.put("topK", limit);
            result.put("vectorSize", embeddingService.dimension());
            result.put("response", response);
        } catch (Exception e) {
            result.put("success", false);
            result.put("collection", "paper_chunks");
            result.put("question", question);
            result.put("error", e.getMessage());
        }
        return result;
    }

    public String searchSimilarChunksRaw(String question, Integer topK) {
        return searchSimilarChunksRaw(question, topK, List.of(), null);
    }

    public String searchSimilarChunksRaw(String question, Integer topK, List<Long> paperIds) {
        return searchSimilarChunksRaw(question, topK, paperIds, null);
    }

    /**
     * 检索相似 chunk，支持 paperId 范围和 contentType 双重过滤。
     *
     * @param contentType null=不限类型(LIBRARY_DISCOVERY); "RAW_CHUNK"=只检索原文(VECTOR_RAG)
     */
    public String searchSimilarChunksRaw(String question, Integer topK, List<Long> paperIds, String contentType) {
        if (question == null || question.isBlank()) {
            throw new RuntimeException("问题不能为空");
        }
        int limit = topK != null && topK > 0 ? topK : 5;
        long embeddingStartedAt = RagTimingTrace.start();
        List<Double> queryVector;
        try {
            queryVector = embeddingService.embed(question);
        } finally {
            RagTimingTrace.addElapsed(RagTimingTrace.EMBEDDING, embeddingStartedAt);
        }

        return searchSimilarChunksRaw(queryVector, limit, paperIds, contentType);
    }

    /**
     * 使用同一个查询向量分别检索多种内容类型。
     *
     * LIBRARY_DISCOVERY 需要画像、章节摘要和原文三个独立候选池；在这里复用一次
     * Embedding，避免按内容类型重复调用远程 Embedding 服务。
     */
    public Map<String, String> searchSimilarChunksByContentTypesRaw(String question,
                                                                    Integer topK,
                                                                    List<Long> paperIds,
                                                                    List<String> contentTypes) {
        if (question == null || question.isBlank()) {
            throw new RuntimeException("问题不能为空");
        }
        if (contentTypes == null || contentTypes.isEmpty()) {
            return Map.of();
        }

        int limit = topK != null && topK > 0 ? topK : 5;
        long embeddingStartedAt = RagTimingTrace.start();
        List<Double> queryVector;
        try {
            queryVector = embeddingService.embed(question);
        } finally {
            RagTimingTrace.addElapsed(RagTimingTrace.EMBEDDING, embeddingStartedAt);
        }
        Map<String, String> responses = new LinkedHashMap<>();
        contentTypes.stream()
                .filter(type -> type != null && !type.isBlank())
                .distinct()
                .forEach(type -> responses.put(
                        type,
                        searchSimilarChunksRaw(queryVector, limit, paperIds, type)
                ));
        return responses;
    }

    private String searchSimilarChunksRaw(List<Double> queryVector,
                                           int limit,
                                           List<Long> paperIds,
                                           String contentType) {

        Map<String, Object> body = new HashMap<>();
        body.put("vector", queryVector);
        body.put("limit", limit);
        body.put("with_payload", true);

        List<Long> scopedPaperIds = normalizePaperIds(paperIds);
        List<Map<String, Object>> mustConditions = new ArrayList<>();

        if (!scopedPaperIds.isEmpty()) {
            mustConditions.add(Map.of(
                    "key", "paperId",
                    "match", Map.of("any", scopedPaperIds)
            ));
        }
        if (contentType != null && !contentType.isBlank()) {
            mustConditions.add(Map.of(
                    "key", "contentType",
                    "match", Map.of("value", contentType)
            ));
        }
        if (!mustConditions.isEmpty()) {
            body.put("filter", Map.of("must", mustConditions));
        }

        long vectorSearchStartedAt = RagTimingTrace.start();
        try {
            return RestClient.create()
                    .post()
                    .uri(qdrantUrl + "/collections/paper_chunks/points/search")
                    .body(body)
                    .retrieve()
                    .body(String.class);
        } finally {
            RagTimingTrace.addElapsed(RagTimingTrace.VECTOR_SEARCH, vectorSearchStartedAt);
        }
    }

    // ===== Helpers =====

    private List<Long> normalizePaperIds(List<Long> paperIds) {
        if (paperIds == null || paperIds.isEmpty()) {
            return List.of();
        }
        return paperIds.stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
    }

    private String previewText(String content) {
        if (content == null) return "";
        String text = sanitizeForJson(content);
        return text.length() <= 300 ? text : text.substring(0, 300);
    }

    private String sanitizeForJson(String text) {
        if (text == null) return "";
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c < 0x20 && c != '\t' && c != '\n' && c != '\r') { sb.append(' '); continue; }
            if (c == 0x7F) { sb.append(' '); continue; }
            if (Character.isHighSurrogate(c) || Character.isLowSurrogate(c)) { sb.append(' '); continue; }
            sb.append(c);
        }
        return sb.toString().replaceAll("\\s+", " ").trim();
    }

    private String safeString(String value, String fallback) {
        String base = (value == null || value.isBlank()) ? fallback : value;
        return sanitizeForJson(base);
    }
}
