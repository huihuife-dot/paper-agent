package com.myagent.assistant.rag.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myagent.assistant.paper.entity.PaperChunk;
import com.myagent.assistant.paper.entity.PaperReference;
import com.myagent.assistant.paper.mapper.PaperChunkMapper;
import com.myagent.assistant.paper.mapper.PaperReferenceMapper;
import com.myagent.assistant.observability.RagTimingTrace;
import com.myagent.assistant.qdrant.service.QdrantService;
import com.myagent.assistant.rag.dto.QueryRewriteResult;
import com.myagent.assistant.rag.dto.RagSource;
import com.myagent.assistant.rag.dto.PaperCandidate;
import com.myagent.assistant.rag.service.Bm25IndexService;
import com.myagent.assistant.rag.service.PaperDiscoveryService;
import com.myagent.assistant.rag.service.QueryRewriteService;
import com.myagent.assistant.rag.service.RagRetrievalService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * RAG 检索服务实现。
 *
 * 检索模式：
 * - VECTOR_RAG：dense 双路 + BM25 → RRF 融合，只检索 RAW_CHUNK
 * - LIBRARY_DISCOVERY：dense 三路改写 + BM25 → RRF 融合，全粒度检索
 */
@Service
public class RagRetrievalServiceImpl implements RagRetrievalService {

    private static final Logger log = LoggerFactory.getLogger(RagRetrievalServiceImpl.class);
    private static final double RRF_K = 60.0;
    private static final List<String> DISCOVERY_CONTENT_TYPES = List.of(
            "PAPER_PROFILE", "SECTION_SUMMARY", "RAW_CHUNK"
    );
    private static final int MAX_DISCOVERY_CANDIDATES = 8;
    private static final int MAX_DISCOVERY_SELECTED_PAPERS = 6;
    private static final Pattern LATIN_TECHNICAL_TERM = Pattern.compile("[A-Za-z][A-Za-z0-9+\\-]{1,}");
    private static final Set<String> GENERIC_QUERY_TERMS = Set.of(
            "paper", "papers", "method", "methods", "model", "models",
            "forecast", "forecasting", "prediction", "wind", "speed", "power",
            "time", "series", "论文", "方法", "模型", "预测"
    );
    private static final List<String> KNOWN_TECHNICAL_TERMS = List.of(
            "VMD", "CEEMD", "CEEMDAN", "小波", "ARMA", "ARIMA", "VAR", "ESN",
            "克里金", "Transformer", "图神经网络", "图消息传递", "光伏", "LSTM"
    );

    private final QdrantService qdrantService;
    private final PaperChunkMapper paperChunkMapper;
    private final PaperReferenceMapper paperReferenceMapper;
    private final ObjectMapper objectMapper;
    private final QueryRewriteService queryRewriteService;
    private final Bm25IndexService bm25IndexService;
    private final PaperDiscoveryService paperDiscoveryService;

    public RagRetrievalServiceImpl(QdrantService qdrantService,
                                   PaperChunkMapper paperChunkMapper,
                                   PaperReferenceMapper paperReferenceMapper,
                                   ObjectMapper objectMapper,
                                   QueryRewriteService queryRewriteService,
                                   Bm25IndexService bm25IndexService,
                                   PaperDiscoveryService paperDiscoveryService) {
        this.qdrantService = qdrantService;
        this.paperChunkMapper = paperChunkMapper;
        this.paperReferenceMapper = paperReferenceMapper;
        this.objectMapper = objectMapper;
        this.queryRewriteService = queryRewriteService;
        this.bm25IndexService = bm25IndexService;
        this.paperDiscoveryService = paperDiscoveryService;
    }

    // ===== VECTOR_RAG 路径：dense 双路 + BM25 → RRF =====

    @Override
    public List<RagSource> retrieveSources(String question, Integer topK) {
        return retrieveSources(question, topK, List.of());
    }

    @Override
    public List<RagSource> retrieveSources(String question, Integer topK, List<Long> paperIds) {
        if (question == null || question.isBlank()) {
            throw new RuntimeException("问题不能为空");
        }

        try {
            int limit = topK != null && topK > 0 ? topK : 5;
            int retrievalLimit = Math.min(limit * 3, 30);
            List<Long> scopedPaperIds = normalizePaperIds(paperIds);

            // Route 1: 原始问题 dense（Qdrant）
            List<RagSource> originalSources = searchSources(question, retrievalLimit, "original", scopedPaperIds, "RAW_CHUNK");

            // Route 2: 改写后 dense（Qdrant）
            long rewriteStartedAt = RagTimingTrace.start();
            String retrievalQuestion;
            try {
                retrievalQuestion = queryRewriteService.rewriteForRetrieval(question);
            } finally {
                RagTimingTrace.addElapsed(RagTimingTrace.QUERY_REWRITE, rewriteStartedAt);
            }
            List<RagSource> rewrittenSources;
            if (retrievalQuestion != null && !retrievalQuestion.isBlank() && !retrievalQuestion.equals(question)) {
                rewrittenSources = searchSources(retrievalQuestion, retrievalLimit, "rewritten", scopedPaperIds, "RAW_CHUNK");
            } else {
                rewrittenSources = List.of();
            }

            // Route 3: BM25 关键词检索（内存倒排索引）
            // 使用改写后的英文 query 做 BM25，因为英文 query 更接近论文术语
            String bm25Query = (retrievalQuestion != null && !retrievalQuestion.isBlank())
                    ? retrievalQuestion : question;
            long bm25StartedAt = RagTimingTrace.start();
            List<RagSource> bm25Sources;
            try {
                bm25Sources = searchSourcesBm25(bm25Query, retrievalLimit, scopedPaperIds);
            } finally {
                RagTimingTrace.addElapsed(RagTimingTrace.BM25, bm25StartedAt);
            }

            // RRF 三路融合
            long fusionStartedAt = RagTimingTrace.start();
            List<RagSource> merged;
            try {
                merged = rrfMerge(
                        List.of(
                                new RouteResult("dense_original", originalSources),
                                new RouteResult("dense_rewritten", rewrittenSources),
                                new RouteResult("bm25", bm25Sources)
                        ),
                        limit
                );
            } finally {
                RagTimingTrace.addElapsed(RagTimingTrace.FUSION_RANKING, fusionStartedAt);
            }

            return merged;

        } catch (Exception e) {
            throw new RuntimeException("RAG 检索失败：" + e.getMessage(), e);
        }
    }

    // ===== LIBRARY_DISCOVERY 路径：dense 三路改写 + BM25 → RRF =====

    @Override
    public List<RagSource> retrieveSourcesWithRewrite(QueryRewriteResult rewriteResult, Integer topK, List<Long> paperIds) {
        if (rewriteResult == null || !rewriteResult.isValid()) {
            throw new RuntimeException("Query Rewrite 结果无效");
        }

        int limit = topK != null && topK > 0 ? topK : 5;
        int retrievalLimit = Math.min(limit * 3, 30);
        List<Long> scopedPaperIds = normalizePaperIds(paperIds);
        List<String> queries = rewriteResult.allQueries();

        // Routes 1~3: 每个改写 query 只生成一次向量，再分别进入画像/摘要/原文候选池。
        Map<String, List<RouteResult>> typedRoutes = new LinkedHashMap<>();
        DISCOVERY_CONTENT_TYPES.forEach(type -> typedRoutes.put(type, new ArrayList<>()));
        for (int i = 0; i < queries.size(); i++) {
            String query = queries.get(i);
            try {
                Map<String, String> responses = qdrantService.searchSimilarChunksByContentTypesRaw(
                        query, retrievalLimit, scopedPaperIds, DISCOVERY_CONTENT_TYPES);
                for (String contentType : DISCOVERY_CONTENT_TYPES) {
                    String routeLabel = "dense_" + (i + 1) + "_" + contentType.toLowerCase();
                    long hydrationStartedAt = RagTimingTrace.start();
                    List<RagSource> sources;
                    try {
                        sources = parseSources(responses.get(contentType), routeLabel);
                    } finally {
                        RagTimingTrace.addElapsed(RagTimingTrace.SOURCE_HYDRATION, hydrationStartedAt);
                    }
                    typedRoutes.get(contentType).add(new RouteResult(routeLabel, sources));
                }
            } catch (Exception e) {
                log.warn("多路检索 dense_{} 失败，降级跳过。query: {}", i + 1, query, e);
            }
        }

        // Route 4: BM25 关键词检索（内存倒排索引，全粒度）
        if (rewriteResult.getKeywordQuery() != null && !rewriteResult.getKeywordQuery().isBlank()) {
            long bm25StartedAt = RagTimingTrace.start();
            List<RagSource> bm25Sources;
            try {
                bm25Sources = searchSourcesBm25(
                        rewriteResult.getKeywordQuery(), retrievalLimit, scopedPaperIds);
            } finally {
                RagTimingTrace.addElapsed(RagTimingTrace.BM25, bm25StartedAt);
            }
            typedRoutes.get("RAW_CHUNK").add(new RouteResult("bm25", bm25Sources));
        }

        long fusionStartedAt = RagTimingTrace.start();
        try {
            List<RagSource> profileSources = rrfMerge(typedRoutes.get("PAPER_PROFILE"), retrievalLimit);
            List<RagSource> summarySources = rrfMerge(typedRoutes.get("SECTION_SUMMARY"), retrievalLimit);
            List<RagSource> rawSources = rrfMerge(typedRoutes.get("RAW_CHUNK"), retrievalLimit);
            List<RagSource> allTypedSources = new ArrayList<>();
            allTypedSources.addAll(profileSources);
            allTypedSources.addAll(summarySources);
            allTypedSources.addAll(rawSources);

            List<String> discoveryTerms = extractDiscoveryTerms(rewriteResult);
            List<PaperCandidate> candidates = paperDiscoveryService.rankCandidatesByEvidence(
                    allTypedSources,
                    rewriteResult.getOriginalQuestion(),
                    discoveryTerms,
                    Math.min(limit, MAX_DISCOVERY_CANDIDATES));
            List<Long> rankedPaperIds = selectCandidatePaperIds(
                    rewriteResult.getOriginalQuestion(), candidates, limit);
            return selectSourcesByPaperRoundRobin(
                    allTypedSources, rankedPaperIds, discoveryTerms, limit);
        } finally {
            RagTimingTrace.addElapsed(RagTimingTrace.FUSION_RANKING, fusionStartedAt);
        }
    }

    // ===== 核心检索 =====

    /**
     * Qdrant dense 检索 → RagSource 转换。
     */
    private List<RagSource> searchSources(String query, Integer topK, String retrievalRoute,
                                          List<Long> paperIds, String contentType) throws Exception {
        String rawResponse = qdrantService.searchSimilarChunksRaw(query, topK, paperIds, contentType);
        long hydrationStartedAt = RagTimingTrace.start();
        try {
            return parseSources(rawResponse, retrievalRoute);
        } finally {
            RagTimingTrace.addElapsed(RagTimingTrace.SOURCE_HYDRATION, hydrationStartedAt);
        }
    }

    private List<RagSource> parseSources(String rawResponse, String retrievalRoute) throws Exception {
        if (rawResponse == null || rawResponse.isBlank()) {
            return List.of();
        }
        JsonNode root = objectMapper.readTree(rawResponse);
        JsonNode resultArray = root.get("result");

        List<RagSource> sources = new ArrayList<>();
        if (resultArray == null || !resultArray.isArray()) {
            return sources;
        }

        for (JsonNode item : resultArray) {
            JsonNode payload = item.get("payload");
            if (payload == null) continue;

            Double score = item.get("score") != null ? item.get("score").asDouble() : null;
            String payloadContentType = payload.has("contentType") ? payload.get("contentType").asText() : "RAW_CHUNK";

            RagSource source;
            if ("PAPER_PROFILE".equals(payloadContentType)) {
                source = buildSourceFromProfile(payload, score, retrievalRoute);
            } else if ("SECTION_SUMMARY".equals(payloadContentType)) {
                source = buildSourceFromSummary(payload, score, retrievalRoute);
            } else {
                source = buildSourceFromChunk(payload, score, retrievalRoute);
            }

            if (source != null) sources.add(source);
        }
        return sources;
    }

    /**
     * 先让每篇候选论文获得一个上下文位置，再按轮次补充第二、第三条证据。
     * 这里仅建立论文覆盖基础；更细的内容类型配额留给下一阶段。
     */
    List<RagSource> selectSourcesByPaperRoundRobin(List<RagSource> sources,
                                                    List<Long> rankedPaperIds,
                                                    List<String> queryTerms,
                                                    int topK) {
        if (sources == null || sources.isEmpty() || rankedPaperIds == null || rankedPaperIds.isEmpty()) {
            return List.of();
        }

        Map<Long, List<RagSource>> sourcesByPaper = new LinkedHashMap<>();
        Map<Long, Set<Long>> sourceKeysByPaper = new HashMap<>();
        Set<Long> allowedPaperIds = new HashSet<>(rankedPaperIds);
        sources.stream()
                .filter(source -> source != null
                        && source.getPaperId() != null
                        && allowedPaperIds.contains(source.getPaperId()))
                .sorted(Comparator.comparing(
                        (RagSource source) -> source.getScore() == null
                                ? Double.NEGATIVE_INFINITY : source.getScore()
                ).reversed())
                .forEach(source -> {
                    Long key = dedupKey(source);
                    if (key != null && sourceKeysByPaper
                            .computeIfAbsent(source.getPaperId(), ignored -> new HashSet<>())
                            .add(key)) {
                        sourcesByPaper
                                .computeIfAbsent(source.getPaperId(), ignored -> new ArrayList<>())
                                .add(source);
                    }
                });

        List<RagSource> selected = new ArrayList<>();
        Set<Long> selectedKeys = new HashSet<>();

        // 第一轮：每篇候选先放入画像，帮助模型判断论文整体主题。
        for (Long paperId : rankedPaperIds) {
            RagSource profile = sourcesByPaper.getOrDefault(paperId, List.of()).stream()
                    .filter(source -> "paper_profile".equalsIgnoreCase(source.getSourceType()))
                    .findFirst()
                    .orElseGet(() -> sourcesByPaper.getOrDefault(paperId, List.of()).stream()
                            .findFirst().orElse(null));
            addSelectedSource(selected, selectedKeys, profile, topK);
        }

        // 第二轮：每篇候选至少补一条摘要或原文，避免画像成为事实判断的唯一依据。
        for (Long paperId : rankedPaperIds) {
            RagSource factualEvidence = sourcesByPaper.getOrDefault(paperId, List.of()).stream()
                    .filter(source -> !"paper_profile".equalsIgnoreCase(source.getSourceType()))
                    .filter(source -> {
                        Long key = dedupKey(source);
                        return key != null && !selectedKeys.contains(key);
                    })
                    .sorted(Comparator
                            // 相关工作/现有方法综述即使命中更多查询词，也不能优先于论文自身的直接方法证据。
                            .comparingInt((RagSource source) -> isBackgroundOnlyEvidence(source) ? 0 : 1)
                            .reversed()
                            .thenComparing(Comparator.comparingInt(
                                    (RagSource source) -> queryTermMatches(source, queryTerms)).reversed())
                            .thenComparing(Comparator.comparingInt(this::factualTypePriority).reversed())
                            .thenComparing(
                                    source -> source.getScore() == null
                                            ? Double.NEGATIVE_INFINITY : source.getScore(),
                                    Comparator.reverseOrder()))
                    .findFirst()
                    .orElse(null);
            addSelectedSource(selected, selectedKeys, factualEvidence, topK);
        }

        // 剩余预算按论文轮询回流，避免再次被单篇论文占满。
        for (int evidenceIndex = 0; selected.size() < topK; evidenceIndex++) {
            boolean addedInRound = false;
            for (Long paperId : rankedPaperIds) {
                List<RagSource> paperSources = sourcesByPaper.getOrDefault(paperId, List.of());
                if (evidenceIndex >= paperSources.size()) {
                    continue;
                }
                RagSource source = paperSources.get(evidenceIndex);
                Long key = dedupKey(source);
                if (key != null && selectedKeys.add(key)) {
                    selected.add(source);
                    addedInRound = true;
                }
                if (selected.size() >= topK) {
                    break;
                }
            }
            if (!addedInRound) {
                break;
            }
        }
        return selected;
    }

    private boolean isBackgroundOnlyEvidence(RagSource source) {
        if (source == null) {
            return false;
        }
        String evidence = ((source.getSectionTitle() == null ? "" : source.getSectionTitle())
                + " " + (source.getContent() == null ? "" : source.getContent())).toLowerCase();
        boolean backgroundMarker = containsAny(evidence,
                "existing method", "existing graph", "current method", "current graph",
                "related work", "state of the art", "previous studies",
                "本节综述", "现有方法", "现有图神经网络", "已有方法");
        boolean directMethodMarker = containsAny(evidence,
                "we propose", "proposed model", "本文提出", "本研究提出",
                "构建图结构", "生成邻接矩阵", "builds a graph", "constructs a graph",
                "adjacency matrix");
        return backgroundMarker && !directMethodMarker;
    }

    private void addSelectedSource(List<RagSource> selected,
                                   Set<Long> selectedKeys,
                                   RagSource source,
                                   int topK) {
        if (source == null || selected.size() >= topK) {
            return;
        }
        Long key = dedupKey(source);
        if (key != null && selectedKeys.add(key)) {
            selected.add(source);
        }
    }

    List<Long> selectCandidatePaperIds(String question,
                                       List<PaperCandidate> candidates,
                                       int topK) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        // “唯一一篇”问题不能直接截取综合分第一名：通用背景论文可能凭借多路命中排在前面，
        // 但真正的唯一答案通常对问题中的判别性术语覆盖更完整。
        if (isUniqueQuestion(question)) {
            return candidates.stream()
                    .max(Comparator.comparingDouble(PaperCandidate::queryTermCoverage)
                            .thenComparingDouble(PaperCandidate::score))
                    .map(candidate -> List.of(candidate.paperId()))
                    .orElseGet(List::of);
        }
        CandidatePolicy policy = candidatePolicy(question);
        int maximum = Math.min(Math.min(policy.maximum(), MAX_DISCOVERY_SELECTED_PAPERS), topK);
        double topScore = candidates.get(0).score();
        double cutoff = topScore * policy.relativeScoreThreshold();

        List<Long> selected = candidates.stream()
                .filter(candidate -> candidate.score() >= cutoff)
                .limit(maximum)
                .map(PaperCandidate::paperId)
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));

        int minimum = Math.min(policy.minimum(), Math.min(maximum, candidates.size()));
        for (PaperCandidate candidate : candidates) {
            if (selected.size() >= minimum) {
                break;
            }
            if (!selected.contains(candidate.paperId())) {
                selected.add(candidate.paperId());
            }
        }
        return selected;
    }

    private CandidatePolicy candidatePolicy(String question) {
        String normalized = question == null ? "" : question.toLowerCase();
        if (isUniqueQuestion(normalized)) {
            return new CandidatePolicy(1, 1, 0.78);
        }
        if (countOccurrences(normalized, "哪篇") >= 2) {
            return new CandidatePolicy(2, 3, 0.68);
        }
        if (containsAny(normalized, "哪一篇", "哪篇", "找出", "which paper")) {
            return new CandidatePolicy(1, 3, 0.75);
        }
        if (containsAny(normalized, "哪些", "列出", "which papers")) {
            return new CandidatePolicy(1, 6, 0.52);
        }
        return new CandidatePolicy(1, 4, 0.65);
    }

    private boolean isUniqueQuestion(String question) {
        String normalized = question == null ? "" : question.toLowerCase();
        return containsAny(normalized, "唯一", "only one", "unique");
    }

    private List<String> extractDiscoveryTerms(QueryRewriteResult rewriteResult) {
        Set<String> terms = new LinkedHashSet<>();
        addDelimitedTerms(terms, rewriteResult.getKeywordTerms());
        addDelimitedTerms(terms, rewriteResult.getKeywordQuery());

        String originalQuestion = rewriteResult.getOriginalQuestion() == null
                ? "" : rewriteResult.getOriginalQuestion();
        Matcher matcher = LATIN_TECHNICAL_TERM.matcher(originalQuestion);
        while (matcher.find()) {
            addQueryTerm(terms, matcher.group());
        }
        for (String knownTerm : KNOWN_TECHNICAL_TERMS) {
            if (originalQuestion.toLowerCase().contains(knownTerm.toLowerCase())) {
                addQueryTerm(terms, knownTerm);
            }
        }
        if (originalQuestion.contains("图")) {
            addQueryTerm(terms, "图结构");
            addQueryTerm(terms, "节点");
            addQueryTerm(terms, "近邻");
        }
        return terms.stream().limit(12).toList();
    }

    private int queryTermMatches(RagSource source, List<String> queryTerms) {
        if (source == null || queryTerms == null || queryTerms.isEmpty()) {
            return 0;
        }
        String evidence = ((source.getPaperTitle() == null ? "" : source.getPaperTitle())
                + " " + (source.getSectionTitle() == null ? "" : source.getSectionTitle())
                + " " + (source.getContent() == null ? "" : source.getContent()))
                .toLowerCase();
        return (int) queryTerms.stream()
                .map(term -> term.toLowerCase().trim())
                .filter(term -> term.length() >= 2 && evidence.contains(term))
                .distinct()
                .count();
    }

    private int factualTypePriority(RagSource source) {
        if (source == null || source.getSourceType() == null) {
            return 0;
        }
        return "section_summary".equalsIgnoreCase(source.getSourceType()) ? 2 : 1;
    }

    private void addDelimitedTerms(Set<String> terms, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        for (String part : value.split("[,，;；\\n\\s]+")) {
            addQueryTerm(terms, part);
        }
    }

    private void addQueryTerm(Set<String> terms, String value) {
        if (value == null) {
            return;
        }
        String normalized = value.trim();
        if (normalized.length() >= 2
                && !GENERIC_QUERY_TERMS.contains(normalized.toLowerCase())) {
            terms.add(normalized);
        }
    }

    private boolean containsAny(String value, String... candidates) {
        for (String candidate : candidates) {
            if (value.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    private int countOccurrences(String value, String target) {
        int count = 0;
        int fromIndex = 0;
        while ((fromIndex = value.indexOf(target, fromIndex)) >= 0) {
            count++;
            fromIndex += target.length();
        }
        return count;
    }

    /**
     * BM25 检索 → RagSource 转换。
     * BM25 直接在内存中按文档文本匹配，不需要经过 Qdrant。
     */
    private List<RagSource> searchSourcesBm25(String query, int topK, List<Long> paperIds) {
        if (!bm25IndexService.isReady() || query == null || query.isBlank()) {
            return List.of();
        }

        List<RagSource> sources = new ArrayList<>();
        for (Map.Entry<Long, Double> hit : bm25IndexService.search(query, topK)) {
            Long chunkId = hit.getKey();
            PaperChunk chunk = paperChunkMapper.selectById(chunkId);
            if (chunk == null) continue;
            if (paperIds != null && !paperIds.isEmpty() && !paperIds.contains(chunk.getPaperId())) continue;
            if (Boolean.TRUE.equals(chunk.getIsReference()) || Boolean.TRUE.equals(chunk.getIsNoise())) continue;

            PaperReference paper = paperReferenceMapper.selectById(chunk.getPaperId());
            RagSource source = new RagSource();
            source.setSourceType("raw_chunk");
            source.setPaperId(chunk.getPaperId());
            source.setPaperTitle(paper != null ? paper.getTitle() : null);
            source.setChunkId(chunk.getId());
            source.setChunkIndex(chunk.getChunkIndex());
            source.setSectionId(chunk.getSectionId());
            source.setSectionTitle(chunk.getSectionTitle());
            source.setSectionType(chunk.getSectionType());
            source.setIsReference(chunk.getIsReference());
            source.setIsNoise(chunk.getIsNoise());
            source.setChunkStrategyVersion(chunk.getChunkStrategyVersion());
            source.setScore(hit.getValue());
            source.setContent(chunk.getContent());
            source.setRetrievalRoute("bm25");
            sources.add(source);
        }
        return sources;
    }

    // ===== RRF 融合 =====

    /**
     * 多路结果 RRF 融合。
     *
     * 每条 route 独立计算 chunk→rank 映射，
     * 然后 RRF_score(chunk) = Σ 1/(k + rank_i)，k=60。
     */
    private List<RagSource> rrfMerge(List<RouteResult> routes, int topK) {
        // chunkId → (routeId → rank)
        Map<Long, Map<String, Integer>> rankings = new HashMap<>();
        Map<Long, RagSource> allSources = new LinkedHashMap<>();

        for (RouteResult route : routes) {
            for (int rank = 0; rank < route.sources.size(); rank++) {
                RagSource source = route.sources.get(rank);
                Long key = dedupKey(source);
                if (key == null) continue;
                rankings.computeIfAbsent(key, k -> new HashMap<>()).put(route.label, rank + 1);
                allSources.putIfAbsent(key, source);
            }
        }

        if (allSources.isEmpty()) return List.of();

        Map<Long, Double> rrfScores = new HashMap<>();
        for (Map.Entry<Long, Map<String, Integer>> entry : rankings.entrySet()) {
            double rrf = 0.0;
            for (Integer rank : entry.getValue().values()) {
                rrf += 1.0 / (RRF_K + rank);
            }
            rrfScores.put(entry.getKey(), rrf);
        }

        return rrfScores.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(topK)
                .map(entry -> {
                    RagSource s = allSources.get(entry.getKey());
                    s.setScore(entry.getValue());
                    return s;
                })
                .toList();
    }

    // ===== Key 生成 =====

    private Long dedupKey(RagSource source) {
        if (source == null) return null;
        if ("paper_profile".equalsIgnoreCase(source.getSourceType())) {
            return source.getProfileId() != null ? -source.getProfileId() : null;
        }
        if ("section_summary".equalsIgnoreCase(source.getSourceType())) {
            return source.getSectionSummaryId() != null ? -(source.getSectionSummaryId() + 10_000_000L) : null;
        }
        return source.getChunkId();
    }

    // ===== Source 构造 =====

    private RagSource buildSourceFromChunk(JsonNode payload, Double score, String retrievalRoute) {
        if (payload.get("chunkId") == null) return null;
        Long chunkId = payload.get("chunkId").asLong();
        PaperChunk chunk = paperChunkMapper.selectById(chunkId);
        if (chunk == null) return null;
        if (Boolean.TRUE.equals(chunk.getIsReference()) || Boolean.TRUE.equals(chunk.getIsNoise())) return null;

        PaperReference paper = paperReferenceMapper.selectById(chunk.getPaperId());
        RagSource source = new RagSource();
        source.setSourceType("raw_chunk");
        source.setPaperId(chunk.getPaperId());
        source.setPaperTitle(paper != null ? paper.getTitle() : null);
        source.setChunkId(chunk.getId());
        source.setChunkIndex(chunk.getChunkIndex());
        source.setSectionId(chunk.getSectionId());
        source.setSectionTitle(chunk.getSectionTitle());
        source.setSectionType(chunk.getSectionType());
        source.setIsReference(chunk.getIsReference());
        source.setIsNoise(chunk.getIsNoise());
        source.setChunkStrategyVersion(chunk.getChunkStrategyVersion());
        source.setScore(score);
        source.setContent(chunk.getContent());
        source.setRetrievalRoute(retrievalRoute);
        return source;
    }

    private RagSource buildSourceFromProfile(JsonNode payload, Double score, String retrievalRoute) {
        Long paperId = payload.has("paperId") ? payload.get("paperId").asLong() : null;
        if (paperId == null) return null;
        PaperReference paper = paperReferenceMapper.selectById(paperId);
        RagSource source = new RagSource();
        source.setSourceType("paper_profile");
        source.setPaperId(paperId);
        source.setPaperTitle(paper != null ? paper.getTitle() : null);
        source.setProfileId(payload.has("profileId") ? payload.get("profileId").asLong() : null);
        source.setProfileVersion(payload.has("profileVersion") ? payload.get("profileVersion").asText() : null);
        source.setScore(score);
        source.setContent(payload.has("text") ? payload.get("text").asText() : "");
        source.setRetrievalRoute(retrievalRoute);
        return source;
    }

    private RagSource buildSourceFromSummary(JsonNode payload, Double score, String retrievalRoute) {
        Long paperId = payload.has("paperId") ? payload.get("paperId").asLong() : null;
        if (paperId == null) return null;
        PaperReference paper = paperReferenceMapper.selectById(paperId);
        RagSource source = new RagSource();
        source.setSourceType("section_summary");
        source.setPaperId(paperId);
        source.setPaperTitle(paper != null ? paper.getTitle() : null);
        source.setSectionSummaryId(payload.has("sectionSummaryId") ? payload.get("sectionSummaryId").asLong() : null);
        source.setSummaryVersion(payload.has("summaryVersion") ? payload.get("summaryVersion").asText() : null);
        source.setSectionId(payload.has("sectionId") ? payload.get("sectionId").asLong() : null);
        source.setSectionType(payload.has("sectionType") ? payload.get("sectionType").asText() : null);
        source.setSectionTitle(payload.has("sectionTitle") ? payload.get("sectionTitle").asText() : null);
        source.setScore(score);
        source.setContent(payload.has("text") ? payload.get("text").asText() : "");
        source.setRetrievalRoute(retrievalRoute);
        return source;
    }

    // ===== 辅助 =====

    private List<Long> normalizePaperIds(List<Long> paperIds) {
        if (paperIds == null || paperIds.isEmpty()) return List.of();
        return paperIds.stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
    }

    /**
     * 一条检索路由的结果。
     */
    private record RouteResult(String label, List<RagSource> sources) {}

    private record CandidatePolicy(int minimum, int maximum, double relativeScoreThreshold) {}
}
