package com.myagent.assistant.paper.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myagent.assistant.paper.entity.PaperAsset;
import com.myagent.assistant.paper.entity.PaperChunk;
import com.myagent.assistant.paper.entity.PaperReproductionFact;
import com.myagent.assistant.paper.mapper.PaperAssetMapper;
import com.myagent.assistant.paper.mapper.PaperChunkMapper;
import com.myagent.assistant.paper.mapper.PaperReferenceMapper;
import com.myagent.assistant.paper.mapper.PaperReproductionFactMapper;
import com.myagent.assistant.paper.reproduction.ReproductionFactExtractor;
import com.myagent.assistant.paper.service.PaperReproductionFactService;
import com.myagent.assistant.paper.service.PaperReproductionSpecService;
import com.myagent.assistant.qdrant.service.QdrantService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class PaperReproductionFactServiceImpl implements PaperReproductionFactService {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Set<String> DEFAULT_USABLE_STATUSES =
            Set.of("EXTRACTED", "CROSS_CHECKED", "USER_CONFIRMED");

    private final PaperReferenceMapper paperReferenceMapper;
    private final PaperAssetMapper paperAssetMapper;
    private final PaperChunkMapper paperChunkMapper;
    private final PaperReproductionFactMapper factMapper;
    private final ReproductionFactExtractor extractor;
    private final QdrantService qdrantService;
    private final PaperReproductionSpecService specService;

    public PaperReproductionFactServiceImpl(PaperReferenceMapper paperReferenceMapper,
                                            PaperAssetMapper paperAssetMapper,
                                            PaperChunkMapper paperChunkMapper,
                                            PaperReproductionFactMapper factMapper,
                                            ReproductionFactExtractor extractor,
                                            QdrantService qdrantService,
                                            PaperReproductionSpecService specService) {
        this.paperReferenceMapper = paperReferenceMapper;
        this.paperAssetMapper = paperAssetMapper;
        this.paperChunkMapper = paperChunkMapper;
        this.factMapper = factMapper;
        this.extractor = extractor;
        this.qdrantService = qdrantService;
        this.specService = specService;
    }

    @Override
    public Map<String, Object> rebuild(Long paperId) {
        requirePaper(paperId);
        specService.invalidateForPaper(paperId);
        List<PaperReproductionFact> candidates = new ArrayList<>();
        List<PaperAsset> assets = paperAssetMapper.selectList(
                new QueryWrapper<PaperAsset>().eq("paper_id", paperId).ne("verification_status", "REJECTED"));
        for (PaperAsset asset : assets) candidates.addAll(extractor.fromAsset(asset));
        List<PaperChunk> chunks = paperChunkMapper.selectList(
                new QueryWrapper<PaperChunk>().eq("paper_id", paperId)
                        .eq("is_reference", false).eq("is_noise", false));
        for (PaperChunk chunk : chunks) candidates.addAll(extractor.fromChunk(chunk));

        List<PaperReproductionFact> merged = mergeAndMarkConflicts(candidates);
        qdrantService.deleteReproductionFactPoints(paperId);
        replaceMysqlFacts(paperId, merged);
        try {
            qdrantService.upsertReproductionFacts(merged);
            for (PaperReproductionFact fact : merged) factMapper.updateById(fact);
        } catch (RuntimeException e) {
            throw new RuntimeException("复现事实已写入 MySQL，但独立 Qdrant 索引失败，可稍后重新构建：" + e.getMessage(), e);
        }
        long inferred = merged.stream().filter(f -> "MODEL_INFERRED".equals(f.getVerificationStatus())).count();
        long conflicts = merged.stream().map(PaperReproductionFact::getConflictGroup)
                .filter(value -> value != null && !value.isBlank()).distinct().count();
        return Map.of(
                "paperId", paperId,
                "factCount", merged.size(),
                "usableFactCount", merged.size() - inferred,
                "modelInferredCount", inferred,
                "conflictGroupCount", conflicts,
                "indexedCount", merged.size(),
                "extractorVersion", ReproductionFactExtractor.VERSION,
                "collection", "paper_reproduction_facts");
    }

    @Transactional
    protected void replaceMysqlFacts(Long paperId, List<PaperReproductionFact> facts) {
        factMapper.delete(new QueryWrapper<PaperReproductionFact>().eq("paper_id", paperId));
        for (PaperReproductionFact fact : facts) factMapper.insert(fact);
    }

    @Override
    public List<PaperReproductionFact> list(Long paperId, boolean includeModelInferred) {
        requirePaper(paperId);
        List<PaperReproductionFact> facts = factMapper.selectList(
                new QueryWrapper<PaperReproductionFact>().eq("paper_id", paperId)
                        .orderByAsc("page_number", "id"));
        return facts.stream().filter(fact -> usable(fact, includeModelInferred)).toList();
    }

    /**
     * Qdrant 只负责找 ID；返回前必须回查 MySQL，过期或已拒绝的点不会直接出现在结果中。
     */
    @Override
    public List<PaperReproductionFact> search(Long paperId, String query, Integer topK,
                                              boolean includeModelInferred) {
        requirePaper(paperId);
        String response = qdrantService.searchReproductionFactsRaw(query, paperId, topK);
        List<Long> ids = parseFactIds(response);
        if (ids.isEmpty()) return List.of();
        Map<Long, PaperReproductionFact> facts = factMapper.selectBatchIds(ids).stream()
                .filter(fact -> paperId.equals(fact.getPaperId()))
                .collect(Collectors.toMap(PaperReproductionFact::getId, fact -> fact));
        return ids.stream().map(facts::get).filter(fact -> fact != null && usable(fact, includeModelInferred)).toList();
    }

    @Override
    public void invalidateForPaper(Long paperId) {
        if (paperId == null) return;
        specService.invalidateForPaper(paperId);
        factMapper.delete(new QueryWrapper<PaperReproductionFact>().eq("paper_id", paperId));
        try {
            qdrantService.deleteReproductionFactPoints(paperId);
        } catch (RuntimeException ignored) {
            // MySQL 回查会过滤孤立点；Qdrant 恢复后重建会先清理整篇论文的旧点。
        }
    }

    List<PaperReproductionFact> mergeAndMarkConflicts(List<PaperReproductionFact> candidates) {
        Map<String, List<PaperReproductionFact>> exactGroups = candidates.stream()
                .filter(this::complete)
                .collect(Collectors.groupingBy(this::exactKey, LinkedHashMap::new, Collectors.toList()));
        List<PaperReproductionFact> merged = new ArrayList<>();
        for (List<PaperReproductionFact> group : exactGroups.values()) {
            PaperReproductionFact best = group.stream().max(Comparator.comparingDouble(
                    fact -> fact.getConfidence() == null ? 0.0 : fact.getConfidence())).orElseThrow();
            best.setSupportingSourcesJson(toSourcesJson(group));
            merged.add(best);
        }
        Map<String, List<PaperReproductionFact>> keyGroups = merged.stream()
                .collect(Collectors.groupingBy(this::logicalKey));
        for (Map.Entry<String, List<PaperReproductionFact>> entry : keyGroups.entrySet()) {
            long distinctValues = entry.getValue().stream().map(this::normalizedValue).distinct().count();
            if (distinctValues > 1) {
                String conflictGroup = "conflict-" + sha256(entry.getKey()).substring(0, 16);
                entry.getValue().forEach(fact -> fact.setConflictGroup(conflictGroup));
            }
        }
        return merged;
    }

    private List<Long> parseFactIds(String response) {
        try {
            JsonNode result = JSON.readTree(response).path("result");
            List<Long> ids = new ArrayList<>();
            if (result.isArray()) for (JsonNode hit : result) {
                JsonNode id = hit.path("payload").path("factId");
                if (id.canConvertToLong()) ids.add(id.asLong());
            }
            return ids;
        } catch (Exception e) {
            throw new RuntimeException("无法解析复现事实检索结果", e);
        }
    }

    private String toSourcesJson(List<PaperReproductionFact> facts) {
        try {
            return JSON.writeValueAsString(facts.stream().map(fact -> Map.of(
                    "sourceKind", fact.getSourceKind(),
                    "sourceId", fact.getSourceId(),
                    "pageNumber", fact.getPageNumber() == null ? -1 : fact.getPageNumber(),
                    "verificationStatus", fact.getVerificationStatus())).toList());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private boolean usable(PaperReproductionFact fact, boolean includeModelInferred) {
        if ("REJECTED".equals(fact.getVerificationStatus())) return false;
        return includeModelInferred || DEFAULT_USABLE_STATUSES.contains(fact.getVerificationStatus());
    }

    private boolean complete(PaperReproductionFact fact) {
        return fact.getPaperId() != null && fact.getSourceId() != null
                && fact.getFactKey() != null && !fact.getFactKey().isBlank()
                && fact.getFactValue() != null && !fact.getFactValue().isBlank()
                && fact.getEvidenceExcerpt() != null && !fact.getEvidenceExcerpt().isBlank();
    }

    private String exactKey(PaperReproductionFact fact) {
        return logicalKey(fact) + "|" + normalizedValue(fact);
    }

    private String logicalKey(PaperReproductionFact fact) {
        return normalize(fact.getFactType()) + "|" + normalize(fact.getFactKey())
                + "|" + normalize(fact.getConditionsJson());
    }

    private String normalizedValue(PaperReproductionFact fact) {
        return normalize(fact.getFactValue());
    }

    private String normalize(String value) {
        return String.valueOf(value == null ? "" : value).replaceAll("\\s+", " ")
                .trim().toLowerCase(Locale.ROOT);
    }

    private void requirePaper(Long paperId) {
        if (paperId == null || paperReferenceMapper.selectById(paperId) == null)
            throw new RuntimeException("文献不存在");
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
