package com.myagent.assistant.rag.service.impl;

import com.myagent.assistant.rag.dto.PaperRelevance;
import com.myagent.assistant.rag.dto.PaperCandidate;
import com.myagent.assistant.rag.dto.RagSource;
import com.myagent.assistant.rag.service.PaperDiscoveryService;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 论文发现服务实现。
 *
 * 核心思路：
 * 1. 按 paperId 分组
 * 2. 每组计算 hitCount、avgScore、maxScore
 * 3. 综合评分 = hitCount（归一化）+ maxScore（直接使用）
 * 4. 按综合评分降序排列
 */
@Service
public class PaperDiscoveryServiceImpl implements PaperDiscoveryService {

    private static final Map<String, Double> TYPE_WEIGHTS = Map.of(
            "paper_profile", 0.40,
            "section_summary", 0.35,
            "raw_chunk", 0.25
    );
    private static final double SECOND_HIT_WEIGHT = 0.25;
    private static final double CROSS_TYPE_BONUS = 0.05;
    private static final double QUERY_TERM_COVERAGE_WEIGHT = 0.20;
    private static final double REVIEW_PENALTY = 0.30;

    @Override
    public List<PaperRelevance> aggregateByPaper(List<RagSource> sources, int topPapers) {
        if (sources == null || sources.isEmpty()) {
            return List.of();
        }

        // 1. 按 paperId 分组（保留插入顺序用 LinkedHashMap）
        Map<Long, List<RagSource>> grouped = new LinkedHashMap<>();
        for (RagSource source : sources) {
            if (source.getPaperId() == null) {
                continue;
            }
            grouped.computeIfAbsent(source.getPaperId(), k -> new ArrayList<>()).add(source);
        }

        if (grouped.isEmpty()) {
            return List.of();
        }

        int maxHits = grouped.values().stream()
                .mapToInt(List::size)
                .max()
                .orElse(1);

        // 2. 为每组计算 PaperRelevance
        List<PaperRelevance> relevances = new ArrayList<>();
        for (Map.Entry<Long, List<RagSource>> entry : grouped.entrySet()) {
            Long paperId = entry.getKey();
            List<RagSource> paperSources = entry.getValue();

            PaperRelevance pr = new PaperRelevance();
            pr.setPaperId(paperId);
            pr.setHitCount(paperSources.size());

            // 从第一个有标题的 source 中取论文标题
            pr.setPaperTitle(paperSources.stream()
                    .map(RagSource::getPaperTitle)
                    .filter(title -> title != null && !title.isBlank())
                    .findFirst()
                    .orElse("未知论文"));

            // 计算分数统计
            DoubleSummaryStatistics stats = paperSources.stream()
                    .map(RagSource::getScore)
                    .filter(Objects::nonNull)
                    .mapToDouble(Double::doubleValue)
                    .summaryStatistics();

            pr.setMaxScore(stats.getCount() > 0 ? stats.getMax() : 0.0);
            pr.setAvgScore(stats.getCount() > 0 ? stats.getAverage() : 0.0);

            // 该论文最相关的 2 个 chunk（按 score 降序）
            pr.setTopChunks(paperSources.stream()
                    .sorted(Comparator.comparing(RagSource::getScore,
                            Comparator.nullsLast(Comparator.reverseOrder())))
                    .limit(2)
                    .collect(Collectors.toList()));

            relevances.add(pr);
        }

        // 3. 综合评分排序：hitCount 归一化 + maxScore（各占 50%）
        relevances.sort((a, b) -> {
            double scoreA = (double) a.getHitCount() / maxHits * 0.5 + a.getMaxScore() * 0.5;
            double scoreB = (double) b.getHitCount() / maxHits * 0.5 + b.getMaxScore() * 0.5;
            return Double.compare(scoreB, scoreA);
        });

        // 4. 截断为 topPapers
        return relevances.stream()
                .limit(Math.max(topPapers, 1))
                .collect(Collectors.toList());
    }

    @Override
    public List<Long> rankPaperIdsByEvidence(List<RagSource> sources, int topPapers) {
        return rankCandidatesByEvidence(sources, null, List.of(), topPapers).stream()
                .map(PaperCandidate::paperId)
                .toList();
    }

    @Override
    public List<PaperCandidate> rankCandidatesByEvidence(List<RagSource> sources,
                                                         String question,
                                                         List<String> queryTerms,
                                                         int topPapers) {
        if (sources == null || sources.isEmpty() || topPapers <= 0) {
            return List.of();
        }

        List<String> normalizedTerms = normalizeQueryTerms(queryTerms);

        Map<String, Double> maxScoreByType = sources.stream()
                .filter(this::hasRankableEvidence)
                .collect(Collectors.toMap(
                        this::normalizedSourceType,
                        source -> safeScore(source.getScore()),
                        Math::max
                ));

        Map<Long, Map<String, List<Double>>> scoresByPaperAndType = new LinkedHashMap<>();
        for (RagSource source : sources) {
            if (!hasRankableEvidence(source)) {
                continue;
            }
            String sourceType = normalizedSourceType(source);
            scoresByPaperAndType
                    .computeIfAbsent(source.getPaperId(), ignored -> new LinkedHashMap<>())
                    .computeIfAbsent(sourceType, ignored -> new ArrayList<>())
                    .add(safeScore(source.getScore()));
        }

        List<PaperCandidate> candidates = new ArrayList<>();
        for (Map.Entry<Long, Map<String, List<Double>>> paperEntry : scoresByPaperAndType.entrySet()) {
            double paperScore = 0.0;
            int coveredTypes = 0;
            for (Map.Entry<String, List<Double>> typeEntry : paperEntry.getValue().entrySet()) {
                String sourceType = typeEntry.getKey();
                double maxTypeScore = maxScoreByType.getOrDefault(sourceType, 0.0);
                if (maxTypeScore <= 0.0) {
                    continue;
                }

                List<Double> sortedScores = typeEntry.getValue().stream()
                        .sorted(Comparator.reverseOrder())
                        .toList();
                double best = sortedScores.get(0);
                double second = sortedScores.size() > 1 ? sortedScores.get(1) : 0.0;
                double normalized = Math.min(1.0, (best + SECOND_HIT_WEIGHT * second) / maxTypeScore);
                paperScore += TYPE_WEIGHTS.getOrDefault(sourceType, 0.0) * normalized;
                coveredTypes++;
            }
            paperScore += Math.max(0, coveredTypes - 1) * CROSS_TYPE_BONUS;
            double termCoverage = queryTermCoverage(
                    paperEntry.getKey(), sources, normalizedTerms);
            paperScore += termCoverage * QUERY_TERM_COVERAGE_WEIGHT;
            if (shouldPenalizeReview(question, paperEntry.getKey(), sources)) {
                paperScore = Math.max(0.0, paperScore - REVIEW_PENALTY);
            }
            candidates.add(new PaperCandidate(
                    paperEntry.getKey(), paperScore, coveredTypes, termCoverage));
        }

        return candidates.stream()
                .sorted(Comparator.comparingDouble(PaperCandidate::score).reversed()
                        .thenComparing(PaperCandidate::paperId))
                .limit(topPapers)
                .toList();
    }

    private List<String> normalizeQueryTerms(List<String> queryTerms) {
        if (queryTerms == null || queryTerms.isEmpty()) {
            return List.of();
        }
        return queryTerms.stream()
                .filter(Objects::nonNull)
                .map(term -> term.toLowerCase(Locale.ROOT).trim())
                .filter(term -> term.length() >= 2)
                .distinct()
                .toList();
    }

    private double queryTermCoverage(Long paperId,
                                     List<RagSource> sources,
                                     List<String> normalizedTerms) {
        if (normalizedTerms.isEmpty()) {
            return 0.0;
        }
        String searchableEvidence = sources.stream()
                .filter(source -> source != null && paperId.equals(source.getPaperId()))
                .filter(source -> !"paper_profile".equals(normalizedSourceType(source)))
                .map(source -> nullToEmpty(source.getPaperTitle()) + " " + nullToEmpty(source.getContent()))
                .collect(Collectors.joining(" "))
                .toLowerCase(Locale.ROOT);
        if (searchableEvidence.isBlank()) {
            return 0.0;
        }
        long matches = normalizedTerms.stream()
                .filter(searchableEvidence::contains)
                .count();
        return (double) matches / normalizedTerms.size();
    }

    private boolean shouldPenalizeReview(String question, Long paperId, List<RagSource> sources) {
        String normalizedQuestion = nullToEmpty(question).toLowerCase(Locale.ROOT);
        if (normalizedQuestion.contains("综述")
                || normalizedQuestion.contains("review")
                || normalizedQuestion.contains("survey")) {
            return false;
        }
        return sources.stream()
                .filter(source -> source != null && paperId.equals(source.getPaperId()))
                .map(RagSource::getPaperTitle)
                .filter(Objects::nonNull)
                .map(title -> title.toLowerCase(Locale.ROOT))
                .anyMatch(title -> title.contains("review")
                        || title.contains("survey")
                        || title.contains("综述"));
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private boolean hasRankableEvidence(RagSource source) {
        return source != null
                && source.getPaperId() != null
                && TYPE_WEIGHTS.containsKey(normalizedSourceType(source));
    }

    private String normalizedSourceType(RagSource source) {
        if (source == null || source.getSourceType() == null || source.getSourceType().isBlank()) {
            return "raw_chunk";
        }
        return source.getSourceType().toLowerCase(Locale.ROOT);
    }

    private double safeScore(Double score) {
        return score == null || score < 0.0 ? 0.0 : score;
    }
}
