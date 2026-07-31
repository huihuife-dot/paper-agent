package com.myagent.assistant.paper.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myagent.assistant.paper.entity.PaperReproductionFact;
import com.myagent.assistant.paper.entity.PaperReproductionSpec;
import com.myagent.assistant.paper.mapper.PaperReferenceMapper;
import com.myagent.assistant.paper.mapper.PaperReproductionFactMapper;
import com.myagent.assistant.paper.mapper.PaperReproductionSpecMapper;
import com.myagent.assistant.paper.reproduction.ReproductionSpecDocument;
import com.myagent.assistant.paper.service.PaperReproductionSpecService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class PaperReproductionSpecServiceImpl implements PaperReproductionSpecService {
    public static final String SPEC_VERSION = "reproduction-spec-v1";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Map<String, List<String>> CRITICAL_TYPES = Map.of(
            "architecture", List.of("ARCHITECTURE", "ARCHITECTURE_COMPONENT", "ARCHITECTURE_CONNECTION"),
            "algorithm", List.of("ALGORITHM_STEP", "IMPLEMENTATION"),
            "hyperparameters", List.of("HYPERPARAMETER", "HYPERPARAMETER_TABLE"),
            "dataset", List.of("DATASET"),
            "preprocessing", List.of("PREPROCESSING"),
            "loss", List.of("LOSS"),
            "metrics", List.of("METRIC"),
            "environment", List.of("ENVIRONMENT"));
    private static final Set<String> TRUSTED_STATUSES =
            Set.of("EXTRACTED", "CROSS_CHECKED", "USER_CONFIRMED");

    private final PaperReferenceMapper paperReferenceMapper;
    private final PaperReproductionFactMapper factMapper;
    private final PaperReproductionSpecMapper specMapper;

    public PaperReproductionSpecServiceImpl(PaperReferenceMapper paperReferenceMapper,
                                            PaperReproductionFactMapper factMapper,
                                            PaperReproductionSpecMapper specMapper) {
        this.paperReferenceMapper = paperReferenceMapper;
        this.factMapper = factMapper;
        this.specMapper = specMapper;
    }

    @Override
    @Transactional
    public ReproductionSpecDocument rebuild(Long paperId) {
        requirePaper(paperId);
        List<PaperReproductionFact> facts = factMapper.selectList(
                new QueryWrapper<PaperReproductionFact>().eq("paper_id", paperId).orderByAsc("page_number", "id"));
        if (facts.isEmpty()) throw new RuntimeException("尚未构建复现事实，请先构建复现事实");

        Map<String, List<PaperReproductionFact>> conflicts = facts.stream()
                .filter(fact -> fact.getConflictGroup() != null && !fact.getConflictGroup().isBlank())
                .collect(Collectors.groupingBy(PaperReproductionFact::getConflictGroup,
                        LinkedHashMap::new, Collectors.toList()));
        List<ReproductionSpecDocument.FactItem> trusted = facts.stream()
                .filter(fact -> fact.getConflictGroup() == null)
                .filter(this::trusted)
                .map(this::item).toList();
        List<ReproductionSpecDocument.FactItem> reviewRequired = facts.stream()
                .filter(fact -> fact.getConflictGroup() == null)
                .filter(fact -> !"MODEL_INFERRED".equals(fact.getVerificationStatus()))
                .filter(fact -> !trusted(fact))
                .map(this::item).toList();
        List<ReproductionSpecDocument.FactItem> inferred = facts.stream()
                .filter(fact -> fact.getConflictGroup() == null)
                .filter(fact -> "MODEL_INFERRED".equals(fact.getVerificationStatus()))
                .map(this::item).toList();
        List<ReproductionSpecDocument.ConflictItem> conflictItems = conflicts.entrySet().stream()
                .map(entry -> new ReproductionSpecDocument.ConflictItem(
                        entry.getKey(), entry.getValue().get(0).getFactKey(),
                        entry.getValue().stream().map(this::item).toList()))
                .toList();

        Set<String> trustedTypes = trusted.stream().map(ReproductionSpecDocument.FactItem::getFactType)
                .collect(Collectors.toSet());
        List<String> missing = CRITICAL_TYPES.entrySet().stream()
                .filter(entry -> entry.getValue().stream().noneMatch(trustedTypes::contains))
                .map(entry -> entry.getKey() + ": no trusted evidence")
                .sorted().toList();
        double criticalCoverage = round((CRITICAL_TYPES.size() - missing.size()) / (double) CRITICAL_TYPES.size());
        long withProvenance = facts.stream().filter(this::hasCompleteProvenance).count();
        double provenanceCoverage = round(facts.isEmpty() ? 0.0 : withProvenance / (double) facts.size());
        String status = status(criticalCoverage, conflictItems.size(), missing.size());
        String sourceRevision = revision(facts);
        List<String> safeDefaults = safeDefaults(missing, conflictItems);

        ReproductionSpecDocument document = new ReproductionSpecDocument(
                SPEC_VERSION, paperId, sourceRevision, status, criticalCoverage, provenanceCoverage,
                trusted, reviewRequired, inferred, conflictItems, missing, safeDefaults);
        PaperReproductionSpec entity = specMapper.selectOne(new QueryWrapper<PaperReproductionSpec>()
                .eq("paper_id", paperId).eq("spec_version", SPEC_VERSION));
        if (entity == null) {
            entity = new PaperReproductionSpec();
            entity.setPaperId(paperId);
            entity.setSpecVersion(SPEC_VERSION);
        }
        entity.setSpecJson(write(document));
        entity.setSourceRevision(sourceRevision);
        entity.setCriticalCoverage(criticalCoverage);
        entity.setProvenanceCoverage(provenanceCoverage);
        entity.setUnresolvedConflictCount(conflictItems.size());
        entity.setMissingCriticalCount(missing.size());
        entity.setStatus(status);
        if (entity.getId() == null) specMapper.insert(entity); else specMapper.updateById(entity);
        return document;
    }

    @Override
    public ReproductionSpecDocument get(Long paperId) {
        requirePaper(paperId);
        PaperReproductionSpec entity = specMapper.selectOne(new QueryWrapper<PaperReproductionSpec>()
                .eq("paper_id", paperId).eq("spec_version", SPEC_VERSION));
        return entity == null ? null : read(entity.getSpecJson());
    }

    @Override
    public ReproductionSpecDocument getOrBuild(Long paperId) {
        ReproductionSpecDocument existing = get(paperId);
        return existing != null ? existing : rebuild(paperId);
    }

    @Override
    public void invalidateForPaper(Long paperId) {
        if (paperId != null) specMapper.delete(
                new QueryWrapper<PaperReproductionSpec>().eq("paper_id", paperId));
    }

    private boolean trusted(PaperReproductionFact fact) {
        if (!TRUSTED_STATUSES.contains(fact.getVerificationStatus())) return false;
        if ("USER_CONFIRMED".equals(fact.getVerificationStatus())
                || "CROSS_CHECKED".equals(fact.getVerificationStatus())) return true;
        return fact.getConfidence() != null && fact.getConfidence() >= 0.70;
    }

    private boolean hasCompleteProvenance(PaperReproductionFact fact) {
        return fact.getSourceId() != null && fact.getSourceKind() != null && !fact.getSourceKind().isBlank()
                && fact.getPageNumber() != null && fact.getEvidenceExcerpt() != null
                && !fact.getEvidenceExcerpt().isBlank();
    }

    private ReproductionSpecDocument.FactItem item(PaperReproductionFact fact) {
        return new ReproductionSpecDocument.FactItem(
                fact.getId(), fact.getFactType(), fact.getFactKey(), fact.getFactValue(), fact.getUnit(),
                fact.getConditionsJson(), fact.getSourceKind(), fact.getSourceId(), fact.getPageNumber(),
                fact.getEvidenceExcerpt(), fact.getConfidence() == null ? 0.0 : fact.getConfidence(),
                fact.getVerificationStatus());
    }

    private String status(double coverage, int conflicts, int missing) {
        if (coverage == 0.0) return "NOT_READY";
        if (conflicts > 0) return "NEEDS_REVIEW";
        if (missing > 0) return "PARTIAL";
        return "READY";
    }

    private List<String> safeDefaults(List<String> missing, List<ReproductionSpecDocument.ConflictItem> conflicts) {
        List<String> defaults = new ArrayList<>();
        defaults.add("Do not claim paper metrics unless the supplied evidence explicitly supports them.");
        if (missing.stream().anyMatch(value -> value.startsWith("dataset:")))
            defaults.add("Use only a tiny synthetic input for smoke checks when the paper dataset is unavailable.");
        if (missing.stream().anyMatch(value -> value.startsWith("hyperparameters:")))
            defaults.add("Expose missing hyperparameters as configurable placeholders; do not present defaults as paper facts.");
        if (!conflicts.isEmpty())
            defaults.add("Do not choose among conflicting facts automatically; surface the alternatives in the handoff.");
        return defaults;
    }

    private String revision(List<PaperReproductionFact> facts) {
        String material = facts.stream().sorted(Comparator.comparing(PaperReproductionFact::getId))
                .map(fact -> fact.getId() + "|" + fact.getFactValue() + "|" + fact.getVerificationStatus()
                        + "|" + fact.getConflictGroup() + "|" + fact.getExtractorVersion()
                        + "|" + fact.getUpdateTime())
                .collect(Collectors.joining("\n"));
        return sha256(SPEC_VERSION + "\n" + material);
    }

    private String write(ReproductionSpecDocument document) {
        try { return JSON.writeValueAsString(document); }
        catch (Exception e) { throw new IllegalStateException("无法序列化复现规格", e); }
    }

    private ReproductionSpecDocument read(String json) {
        try { return JSON.readValue(json, ReproductionSpecDocument.class); }
        catch (Exception e) { throw new IllegalStateException("无法读取复现规格", e); }
    }

    private void requirePaper(Long paperId) {
        if (paperId == null || paperReferenceMapper.selectById(paperId) == null)
            throw new RuntimeException("文献不存在");
    }

    private double round(double value) { return Math.round(value * 10000.0) / 10000.0; }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
}
