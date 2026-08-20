package com.myagent.assistant.rag.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.myagent.assistant.knowledge.entity.PaperCatalog;
import com.myagent.assistant.knowledge.entity.PaperKnowledgeUnit;
import com.myagent.assistant.knowledge.mapper.PaperCatalogMapper;
import com.myagent.assistant.knowledge.service.PaperKnowledgeService;
import com.myagent.assistant.paper.entity.PaperProfile;
import com.myagent.assistant.paper.entity.PaperReference;
import com.myagent.assistant.paper.entity.PaperSectionSummary;
import com.myagent.assistant.paper.mapper.PaperProfileMapper;
import com.myagent.assistant.paper.mapper.PaperReferenceMapper;
import com.myagent.assistant.paper.mapper.PaperSectionSummaryMapper;
import com.myagent.assistant.rag.context.StructuredEvidenceContext;
import com.myagent.assistant.rag.dto.EvidenceQueryPlan;
import com.myagent.assistant.rag.dto.RagSource;
import com.myagent.assistant.rag.service.EvidenceQueryPlanner;
import com.myagent.assistant.rag.service.RagRetrievalService;
import com.myagent.assistant.rag.service.StructuredEvidenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 结构化优先的证据装配器。
 *
 * 先从目录、画像、知识单元和章节摘要读取确定性数据；只有直接证据覆盖不足或问题置信度低时，
 * 才调用现有 Qdrant/BM25 检索补漏。所有最终证据仍是可追溯的业务对象。
 */
@Service
public class StructuredEvidenceServiceImpl implements StructuredEvidenceService {

    private static final Logger log = LoggerFactory.getLogger(StructuredEvidenceServiceImpl.class);
    private static final String PROFILE_VERSION = "paper-profile-v1";
    private static final String SUMMARY_VERSION = "section-summary-v1";
    private static final Pattern ENGLISH_TERM_PATTERN = Pattern.compile("[A-Za-z][A-Za-z0-9+_.-]{1,}");
    private static final Set<String> STOP_TERMS = Set.of(
            "论文", "哪些", "什么", "如何", "使用", "进行", "相关", "研究", "方法", "模型", "结果",
            "数据", "实验", "分析", "比较", "介绍", "一个", "这个", "这些", "当前", "主要"
    );

    private final EvidenceQueryPlanner queryPlanner;
    private final PaperKnowledgeService knowledgeService;
    private final PaperCatalogMapper catalogMapper;
    private final PaperReferenceMapper paperMapper;
    private final PaperProfileMapper profileMapper;
    private final PaperSectionSummaryMapper summaryMapper;
    private final RagRetrievalService retrievalService;
    private final boolean enabled;

    public StructuredEvidenceServiceImpl(EvidenceQueryPlanner queryPlanner,
                                         PaperKnowledgeService knowledgeService,
                                         PaperCatalogMapper catalogMapper,
                                         PaperReferenceMapper paperMapper,
                                         PaperProfileMapper profileMapper,
                                         PaperSectionSummaryMapper summaryMapper,
                                         RagRetrievalService retrievalService,
                                         @Value("${rag.structured-first-enabled:true}") boolean enabled) {
        this.queryPlanner = queryPlanner;
        this.knowledgeService = knowledgeService;
        this.catalogMapper = catalogMapper;
        this.paperMapper = paperMapper;
        this.profileMapper = profileMapper;
        this.summaryMapper = summaryMapper;
        this.retrievalService = retrievalService;
        this.enabled = enabled;
    }

    @Override
    public StructuredEvidenceContext build(String question, List<Long> paperIds, int topK) {
        StructuredEvidenceContext context = new StructuredEvidenceContext();
        if (!enabled) return context;

        EvidenceQueryPlan plan = queryPlanner.plan(question, paperIds);
        context.setPlan(plan);
        List<Long> requestedPaperIds = normalizeIds(paperIds);
        List<Long> candidatePaperIds = requestedPaperIds.isEmpty()
                ? discoverCandidatePapers(question, plan, Math.max(3, Math.min(topK, 8)))
                : requestedPaperIds;
        plan.setCandidatePaperIds(candidatePaperIds);
        if (candidatePaperIds.isEmpty()) {
            plan.setRagUsed(true);
            plan.setRagReason("目录和结构化知识中还没有可用候选，回退到原有全库RAG");
            return context;
        }

        List<RagSource> structuredSources = buildStructuredSources(plan, candidatePaperIds, topK);
        Set<Long> coveredPaperIds = structuredSources.stream().map(RagSource::getPaperId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        List<Long> missingPaperIds = candidatePaperIds.stream().filter(id -> !coveredPaperIds.contains(id)).toList();
        boolean needsRag = shouldSupplementWithRag(plan, structuredSources, candidatePaperIds, missingPaperIds);
        List<RagSource> ragSources = List.of();
        String ragReason = null;
        if (needsRag) {
            List<Long> ragScope = !missingPaperIds.isEmpty() ? missingPaperIds : candidatePaperIds;
            try {
                ragSources = retrievalService.retrieveSources(question, Math.max(2, topK), ragScope);
                ragReason = ragReason(plan, structuredSources, missingPaperIds);
            } catch (RuntimeException e) {
                ragReason = "结构化证据不足，但语义补漏失败：" + e.getMessage();
                log.warn("结构化证据 RAG 补漏失败: {}", e.getMessage());
            }
        }

        List<RagSource> merged = mergeSources(structuredSources, ragSources, Math.max(topK * 2, 8));
        if (merged.isEmpty()) return context;

        plan.setStructuredSourceCount(structuredSources.size());
        plan.setRagSourceCount(ragSources.size());
        plan.setRagUsed(!ragSources.isEmpty());
        plan.setRagReason(ragReason);
        context.setHandled(true);
        context.setContextStrategy("STRUCTURED_FIRST_" + plan.getScope());
        context.setPaperIds(merged.stream().map(RagSource::getPaperId).filter(Objects::nonNull).distinct().toList());
        context.setSources(merged);
        context.setContextText(buildContextText(plan, merged));
        context.setTokenCount(estimateTokens(merged));
        return context;
    }

    private List<RagSource> buildStructuredSources(EvidenceQueryPlan plan, List<Long> paperIds, int topK) {
        List<RagSource> sources = new ArrayList<>();
        if ("PAPER_PROFILE".equals(plan.getPrimaryLayer()) || "PROFILE_AND_SECTION".equals(plan.getPrimaryLayer())
                || "PAPER_CATALOG".equals(plan.getPrimaryLayer())) {
            sources.addAll(loadProfiles(paperIds));
        }

        if ("KNOWLEDGE_UNIT".equals(plan.getPrimaryLayer()) || "PAPER_CATALOG".equals(plan.getPrimaryLayer())) {
            List<PaperKnowledgeUnit> units = knowledgeService.findUnits(
                    paperIds, plan.getTargetKnowledgeTypes(), Math.max(2, Math.min(topK, 6)));
            sources.addAll(toKnowledgeSources(units));
        }

        if ("PROFILE_AND_SECTION".equals(plan.getPrimaryLayer())) {
            sources.addAll(loadSectionSummaries(paperIds, plan.getTargetSectionTypes(), 4));
        }

        Set<Long> covered = sources.stream().map(RagSource::getPaperId).filter(Objects::nonNull).collect(Collectors.toSet());
        List<Long> missing = paperIds.stream().filter(id -> !covered.contains(id)).toList();
        if (!missing.isEmpty() || sources.isEmpty()) {
            List<Long> target = missing.isEmpty() ? paperIds : missing;
            sources.addAll(loadSectionSummaries(target, plan.getTargetSectionTypes(), 3));
        }

        return sources;
    }

    private List<Long> discoverCandidatePapers(String question, EvidenceQueryPlan plan, int limit) {
        List<PaperCatalog> catalogs = catalogMapper.selectList(new QueryWrapper<PaperCatalog>()
                .in("status", List.of("COMPLETED", "PARTIAL"))
                .orderByDesc("build_time"));
        if (catalogs.isEmpty()) return List.of();
        List<String> terms = extractTerms(question);
        Map<Long, PaperReference> papers = paperMapper.selectBatchIds(catalogs.stream()
                        .map(PaperCatalog::getPaperId).toList())
                .stream().collect(Collectors.toMap(PaperReference::getId, paper -> paper));
        return catalogs.stream()
                .map(catalog -> new CatalogCandidate(catalog.getPaperId(), scoreCatalog(catalog, papers.get(catalog.getPaperId()), terms)))
                .filter(candidate -> terms.isEmpty() || candidate.score() > 0)
                .sorted(Comparator.comparingDouble(CatalogCandidate::score).reversed()
                        .thenComparing(CatalogCandidate::paperId))
                .limit(limit)
                .map(CatalogCandidate::paperId)
                .toList();
    }

    private double scoreCatalog(PaperCatalog catalog, PaperReference paper, List<String> terms) {
        String haystack = normalize(join(
                paper == null ? null : paper.getTitle(),
                paper == null ? null : paper.getKeywords(),
                catalog.getResearchDomains(), catalog.getResearchTasks(), catalog.getMethodTags(),
                catalog.getDatasetTags(), catalog.getMetricTags(), catalog.getCatalogText()));
        if (terms.isEmpty()) return 1.0 + (paper != null && paper.getPublishYear() != null ? paper.getPublishYear() / 10000.0 : 0);
        double score = 0;
        for (String term : terms) {
            String normalizedTerm = normalize(term);
            if (normalizedTerm.isBlank()) continue;
            if (haystack.contains(normalizedTerm)) score += normalizedTerm.length() >= 4 ? 2.0 : 1.0;
        }
        return score;
    }

    private List<RagSource> loadProfiles(List<Long> paperIds) {
        if (paperIds.isEmpty()) return List.of();
        Map<Long, PaperReference> papers = paperMapper.selectBatchIds(paperIds).stream()
                .collect(Collectors.toMap(PaperReference::getId, paper -> paper));
        return profileMapper.selectList(new QueryWrapper<PaperProfile>()
                        .in("paper_id", paperIds).eq("profile_version", PROFILE_VERSION))
                .stream().map(profile -> {
                    RagSource source = new RagSource();
                    source.setSourceType("paper_profile");
                    source.setPaperId(profile.getPaperId());
                    source.setPaperTitle(titleOf(papers.get(profile.getPaperId())));
                    source.setProfileId(profile.getId());
                    source.setProfileVersion(profile.getProfileVersion());
                    source.setContent(clip(prefer(profile.getProfileText(), join(profile.getResearchProblem(),
                            profile.getMethodSummary(), profile.getExperimentSummary(), profile.getKeyContributions(),
                            profile.getLimitations())), 1800));
                    source.setRetrievalRoute("structured_profile");
                    source.setScore(1.0);
                    return source;
                }).toList();
    }

    private List<RagSource> loadSectionSummaries(List<Long> paperIds, List<String> sectionTypes, int limitPerPaper) {
        if (paperIds.isEmpty()) return List.of();
        QueryWrapper<PaperSectionSummary> query = new QueryWrapper<PaperSectionSummary>()
                .in("paper_id", paperIds).eq("summary_version", SUMMARY_VERSION)
                .orderByAsc("paper_id").orderByAsc("section_id");
        List<PaperSectionSummary> summaries = summaryMapper.selectList(query);
        Set<String> targetTypes = sectionTypes == null ? Set.of() : sectionTypes.stream()
                .filter(Objects::nonNull).map(this::normalizeSection).collect(Collectors.toSet());
        Map<Long, PaperReference> papers = paperMapper.selectBatchIds(paperIds).stream()
                .collect(Collectors.toMap(PaperReference::getId, paper -> paper));
        Map<Long, Integer> counts = new LinkedHashMap<>();
        List<RagSource> sources = new ArrayList<>();
        for (PaperSectionSummary summary : summaries) {
            if (!targetTypes.isEmpty() && !matchesSection(summary.getSectionType(), targetTypes)) continue;
            int count = counts.getOrDefault(summary.getPaperId(), 0);
            if (count >= limitPerPaper) continue;
            RagSource source = new RagSource();
            source.setSourceType("section_summary");
            source.setPaperId(summary.getPaperId());
            source.setPaperTitle(titleOf(papers.get(summary.getPaperId())));
            source.setSectionSummaryId(summary.getId());
            source.setSummaryVersion(summary.getSummaryVersion());
            source.setSectionId(summary.getSectionId());
            source.setSectionTitle(summary.getSectionTitle());
            source.setSectionType(summary.getSectionType());
            source.setContent(clip(join(summary.getSummary(), summary.getKeyPoints()), 1400));
            source.setRetrievalRoute("structured_section");
            source.setScore(0.95);
            sources.add(source);
            counts.put(summary.getPaperId(), count + 1);
        }
        return sources;
    }

    private List<RagSource> toKnowledgeSources(List<PaperKnowledgeUnit> units) {
        if (units.isEmpty()) return List.of();
        Map<Long, PaperReference> papers = paperMapper.selectBatchIds(units.stream()
                        .map(PaperKnowledgeUnit::getPaperId).distinct().toList())
                .stream().collect(Collectors.toMap(PaperReference::getId, paper -> paper));
        return units.stream().map(unit -> {
            RagSource source = new RagSource();
            source.setSourceType("knowledge_unit");
            source.setPaperId(unit.getPaperId());
            source.setPaperTitle(titleOf(papers.get(unit.getPaperId())));
            source.setKnowledgeUnitId(unit.getId());
            source.setSectionId(unit.getSectionId());
            source.setChunkId(unit.getChunkId());
            source.setConfidenceLevel(unit.getConfidenceLevel());
            source.setPageNumber(unit.getPageNumber());
            source.setSectionType(unit.getKnowledgeType());
            source.setContent(clip(formatKnowledge(unit), 1200));
            source.setRetrievalRoute("structured_knowledge");
            source.setScore(confidenceScore(unit.getConfidenceLevel()));
            return source;
        }).toList();
    }

    private boolean shouldSupplementWithRag(EvidenceQueryPlan plan,
                                            List<RagSource> structuredSources,
                                            List<Long> candidates,
                                            List<Long> missingPaperIds) {
        if (structuredSources.isEmpty()) return true;
        if (!missingPaperIds.isEmpty()) return true;
        if ("LOW".equals(plan.getConfidence())) return true;
        if ("LIBRARY".equals(plan.getScope()) && structuredSources.size() < Math.min(3, candidates.size())) return true;
        return false;
    }

    private String ragReason(EvidenceQueryPlan plan, List<RagSource> structuredSources, List<Long> missingPaperIds) {
        if (!missingPaperIds.isEmpty()) return "部分论文缺少目标类型的结构化证据，RAG只补充这些论文";
        if ("LOW".equals(plan.getConfidence())) return "问题表达较模糊，使用RAG补充同义表达和隐含关联";
        if (structuredSources.isEmpty()) return "结构化知识未命中，回退RAG定位原文";
        return "结构化候选不足，使用RAG补漏";
    }

    private List<RagSource> mergeSources(List<RagSource> structured, List<RagSource> rag, int limit) {
        Map<String, RagSource> merged = new LinkedHashMap<>();
        for (RagSource source : structured) merged.put(sourceKey(source), source);
        for (RagSource source : rag) merged.putIfAbsent(sourceKey(source), source);
        return merged.values().stream().limit(limit).toList();
    }

    private String sourceKey(RagSource source) {
        if (source.getKnowledgeUnitId() != null) return "knowledge:" + source.getKnowledgeUnitId();
        if (source.getProfileId() != null) return "profile:" + source.getProfileId();
        if (source.getSectionSummaryId() != null) return "summary:" + source.getSectionSummaryId();
        if (source.getChunkId() != null) return "chunk:" + source.getChunkId();
        return source.getSourceType() + ":" + source.getPaperId() + ":"
                + (source.getContent() == null ? 0 : source.getContent().hashCode());
    }

    private String buildContextText(EvidenceQueryPlan plan, List<RagSource> sources) {
        StringBuilder context = new StringBuilder();
        context.append("查询计划：").append(plan.getExplanation()).append("\n");
        int index = 1;
        for (RagSource source : sources) {
            context.append("\n[来源 ").append(index++).append("]\n")
                    .append("论文ID：").append(source.getPaperId()).append("\n")
                    .append("论文标题：").append(source.getPaperTitle()).append("\n")
                    .append("证据层级：").append(source.getSourceType()).append("\n")
                    .append("取证路径：").append(source.getRetrievalRoute()).append("\n");
            if (source.getSectionTitle() != null) context.append("章节：").append(source.getSectionTitle()).append("\n");
            if (source.getPageNumber() != null) context.append("页码：").append(source.getPageNumber()).append("\n");
            if (source.getConfidenceLevel() != null) context.append("可信等级：").append(source.getConfidenceLevel()).append("\n");
            context.append("内容：").append(source.getContent()).append("\n");
        }
        return context.toString();
    }

    private int estimateTokens(List<RagSource> sources) {
        return sources.stream().map(RagSource::getContent).filter(Objects::nonNull)
                .mapToInt(value -> Math.max(1, (int) Math.ceil(value.length() / 4.0))).sum();
    }

    private List<String> extractTerms(String question) {
        LinkedHashSet<String> terms = new LinkedHashSet<>();
        String input = question == null ? "" : question;
        Matcher matcher = ENGLISH_TERM_PATTERN.matcher(input);
        while (matcher.find()) {
            String term = matcher.group().trim();
            if (term.length() >= 2 && !STOP_TERMS.contains(term.toLowerCase(Locale.ROOT))) terms.add(term);
        }
        String chinese = input.replaceAll("[^\\p{IsHan}]", " ");
        List<String> stopTerms = STOP_TERMS.stream().sorted(Comparator.comparingInt(String::length).reversed()).toList();
        for (String stopTerm : stopTerms) chinese = chinese.replace(stopTerm, " ");
        chinese = chinese.replace("请问", " ").replace("帮我", " ").replace("一下", " ")
                .replace("哪几篇", " ").replace("有没有", " ");
        for (String segment : chinese.split("\\s+")) {
            String term = segment.replaceAll("^[请把给]", "").replaceAll("[有吗呢]$", "").trim();
            if (term.length() >= 2) terms.add(term);
        }
        return new ArrayList<>(terms);
    }

    private String formatKnowledge(PaperKnowledgeUnit unit) {
        return join("类型=" + unit.getKnowledgeType(),
                "主语=" + unit.getSubjectText(),
                "关系=" + unit.getPredicateText(),
                "内容=" + unit.getObjectValue(),
                unit.getValueUnit() == null ? null : "单位=" + unit.getValueUnit(),
                unit.getApplicableCondition() == null ? null : "条件=" + unit.getApplicableCondition(),
                unit.getEvidenceText() == null ? null : "证据摘录=" + unit.getEvidenceText());
    }

    private boolean matchesSection(String sectionType, Set<String> targets) {
        String normalized = normalizeSection(sectionType);
        return targets.stream().anyMatch(target -> normalized.contains(target) || target.contains(normalized));
    }

    private String normalizeSection(String value) {
        return value == null ? "" : value.toUpperCase(Locale.ROOT).replaceAll("[^A-Z]", "");
    }

    private double confidenceScore(String level) {
        return switch (level == null ? "" : level) {
            case "GOLD" -> 1.0;
            case "SILVER" -> 0.9;
            default -> 0.75;
        };
    }

    private String titleOf(PaperReference paper) {
        return paper == null ? "未知论文" : prefer(paper.getTitle(), paper.getFileName());
    }

    private List<Long> normalizeIds(List<Long> ids) {
        if (ids == null) return List.of();
        return ids.stream().filter(id -> id != null && id > 0).distinct().toList();
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[\\s，,。；;：:（）()\\[\\]{}\"']", "");
    }

    private String join(String... values) {
        return java.util.Arrays.stream(values).filter(Objects::nonNull).map(String::trim)
                .filter(value -> !value.isBlank()).collect(Collectors.joining("\n"));
    }

    private String prefer(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String clip(String value, int maxChars) {
        if (value == null || value.length() <= maxChars) return value;
        return value.substring(0, maxChars) + "……";
    }

    private record CatalogCandidate(Long paperId, double score) { }
}
