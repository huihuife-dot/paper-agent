package com.myagent.assistant.rag.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.myagent.assistant.paper.entity.PaperProfile;
import com.myagent.assistant.paper.entity.PaperReference;
import com.myagent.assistant.paper.entity.PaperSectionSummary;
import com.myagent.assistant.paper.mapper.PaperProfileMapper;
import com.myagent.assistant.paper.mapper.PaperReferenceMapper;
import com.myagent.assistant.paper.mapper.PaperSectionSummaryMapper;
import com.myagent.assistant.rag.context.HybridRagContext;
import com.myagent.assistant.rag.context.HybridRagContextRequest;
import com.myagent.assistant.rag.dto.RagSource;
import com.myagent.assistant.rag.service.HybridRagContextService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 多篇论文混合 RAG 上下文构造服务实现。
 */
@Service
public class HybridRagContextServiceImpl implements HybridRagContextService {

    private static final String PAPER_PROFILE_VERSION = "paper-profile-v1";
    private static final String SECTION_SUMMARY_VERSION = "section-summary-v1";
    private static final int DEFAULT_MAX_SECTION_SUMMARIES_PER_PAPER = 4;
    private static final int DEFAULT_MAX_RAW_CHUNKS_PER_PAPER = 2;

    private final PaperReferenceMapper paperReferenceMapper;
    private final PaperProfileMapper paperProfileMapper;
    private final PaperSectionSummaryMapper paperSectionSummaryMapper;

    public HybridRagContextServiceImpl(PaperReferenceMapper paperReferenceMapper,
                                       PaperProfileMapper paperProfileMapper,
                                       PaperSectionSummaryMapper paperSectionSummaryMapper) {
        this.paperReferenceMapper = paperReferenceMapper;
        this.paperProfileMapper = paperProfileMapper;
        this.paperSectionSummaryMapper = paperSectionSummaryMapper;
    }

    @Override
    public HybridRagContext buildContext(HybridRagContextRequest request) {
        if (request == null || request.getQuestion() == null || request.getQuestion().isBlank()) {
            throw new RuntimeException("问题不能为空");
        }

        List<Long> paperIds = normalizePaperIds(request.getPaperIds());
        if (paperIds.isEmpty()) {
            throw new RuntimeException("paperIds 不能为空");
        }

        int maxSummaries = positiveOrDefault(
                request.getMaxSectionSummariesPerPaper(),
                DEFAULT_MAX_SECTION_SUMMARIES_PER_PAPER
        );
        int maxRawChunks = positiveOrDefault(
                request.getMaxRawChunksPerPaper(),
                DEFAULT_MAX_RAW_CHUNKS_PER_PAPER
        );

        Map<Long, List<RagSource>> rawSourcesByPaper = groupRawSources(request.getRawChunkSources());
        StringBuilder context = new StringBuilder();
        List<RagSource> sources = new ArrayList<>();

        for (Long paperId : paperIds) {
            PaperReference paper = paperReferenceMapper.selectById(paperId);
            if (paper == null) {
                throw new RuntimeException("文献不存在");
            }

            PaperProfile profile = paperProfileMapper.selectOne(new QueryWrapper<PaperProfile>()
                    .eq("paper_id", paperId)
                    .eq("profile_version", PAPER_PROFILE_VERSION));
            if (profile == null) {
                throw new RuntimeException("文献画像不存在");
            }

            List<PaperSectionSummary> summaries = paperSectionSummaryMapper.selectList(
                    new QueryWrapper<PaperSectionSummary>()
                            .eq("paper_id", paperId)
                            .eq("summary_version", SECTION_SUMMARY_VERSION)
                            .orderByAsc("section_id")
            );
            List<PaperSectionSummary> selectedSummaries = selectRelevantSummaries(
                    request.getQuestion(),
                    summaries,
                    maxSummaries
            );
            List<RagSource> selectedRawSources = rawSourcesByPaper.getOrDefault(paperId, List.of())
                    .stream()
                    .limit(maxRawChunks)
                    .toList();

            appendPaperHeader(context, paperId, paper);
            appendProfile(context, profile);
            sources.add(profileSource(paper, profile));
            appendSummaries(context, selectedSummaries);
            selectedSummaries.forEach(summary -> sources.add(summarySource(paper, summary)));
            appendRawChunks(context, selectedRawSources);
            selectedRawSources.forEach(source -> sources.add(rawChunkSource(source)));
            context.append("\n");
        }

        String contextText = context.toString().trim();
        return new HybridRagContext(
                contextText,
                sources,
                estimateTokens(contextText),
                paperIds
        );
    }

    private void appendPaperHeader(StringBuilder context, Long paperId, PaperReference paper) {
        context.append("[Paper ").append(paperId).append("] ")
                .append(nullToDefault(paper.getTitle(), "Untitled"))
                .append("\n");
    }

    private void appendProfile(StringBuilder context, PaperProfile profile) {
        context.append("一、文献画像\n");
        appendLine(context, "研究问题", profile.getResearchProblem());
        appendLine(context, "方法概述", profile.getMethodSummary());
        appendLine(context, "实验评估", profile.getExperimentSummary());
        appendLine(context, "主要贡献", profile.getKeyContributions());
        appendLine(context, "局限性", profile.getLimitations());
        appendLine(context, "关键词", profile.getKeywords());
        appendLine(context, "画像文本", profile.getProfileText());
        context.append("\n");
    }

    private void appendSummaries(StringBuilder context, List<PaperSectionSummary> summaries) {
        if (summaries.isEmpty()) {
            return;
        }
        context.append("二、相关章节摘要\n");
        for (PaperSectionSummary summary : summaries) {
            context.append("[")
                    .append(nullToDefault(summary.getSectionType(), "UNKNOWN"))
                    .append("] ")
                    .append(nullToDefault(summary.getSectionTitle(), "Untitled"))
                    .append("\n");
            appendLine(context, "摘要", summary.getSummary());
            appendLine(context, "要点", summary.getKeyPoints());
        }
        context.append("\n");
    }

    private void appendRawChunks(StringBuilder context, List<RagSource> rawSources) {
        if (rawSources.isEmpty()) {
            return;
        }
        context.append("三、原文证据片段\n");
        for (RagSource source : rawSources) {
            context.append("[chunk ")
                    .append(source.getChunkId())
                    .append(", score=")
                    .append(source.getScore())
                    .append("] ")
                    .append(clip(source.getContent(), 800))
                    .append("\n");
        }
        context.append("\n");
    }

    private List<PaperSectionSummary> selectRelevantSummaries(String question,
                                                              List<PaperSectionSummary> summaries,
                                                              int limit) {
        if (summaries == null || summaries.isEmpty()) {
            return List.of();
        }
        return summaries.stream()
                .sorted(Comparator
                        .comparingInt((PaperSectionSummary summary) -> priorityOf(question, summary.getSectionType()))
                        .thenComparing(summary -> summary.getSectionId() == null ? Long.MAX_VALUE : summary.getSectionId()))
                .limit(limit)
                .toList();
    }

    private int priorityOf(String question, String sectionType) {
        List<String> priorities = sectionPriorities(question);
        String normalizedType = nullToDefault(sectionType, "UNKNOWN").toUpperCase();
        int index = priorities.indexOf(normalizedType);
        return index >= 0 ? index : priorities.size() + 1;
    }

    private List<String> sectionPriorities(String question) {
        String normalizedQuestion = nullToDefault(question, "").toLowerCase();
        if (containsAny(normalizedQuestion, "方法", "method", "模型", "框架")) {
            return List.of("METHOD", "RELATED_WORK", "INTRODUCTION");
        }
        if (containsAny(normalizedQuestion, "实验", "结果", "experiment", "result", "性能")) {
            return List.of("EXPERIMENT", "RESULT", "DISCUSSION");
        }
        if (containsAny(normalizedQuestion, "局限", "不足", "缺点", "limitation", "weakness")) {
            return List.of("DISCUSSION", "CONCLUSION", "RESULT", "EXPERIMENT");
        }
        if (containsAny(normalizedQuestion, "创新", "贡献", "contribution", "novel")) {
            return List.of("ABSTRACT", "INTRODUCTION", "METHOD", "CONCLUSION");
        }
        if (containsAny(normalizedQuestion, "对比", "比较", "区别", "compare", "difference")) {
            return List.of("METHOD", "EXPERIMENT", "RESULT", "DISCUSSION", "CONCLUSION");
        }
        return List.of("ABSTRACT", "INTRODUCTION", "METHOD", "EXPERIMENT", "RESULT", "CONCLUSION");
    }

    private Map<Long, List<RagSource>> groupRawSources(List<RagSource> rawSources) {
        Map<Long, List<RagSource>> result = new LinkedHashMap<>();
        if (rawSources == null || rawSources.isEmpty()) {
            return result;
        }
        rawSources.stream()
                .filter(source -> source != null && source.getPaperId() != null)
                .sorted(Comparator.comparing(
                        (RagSource source) -> source.getScore() == null ? Double.NEGATIVE_INFINITY : source.getScore()
                ).reversed())
                .forEach(source -> result.computeIfAbsent(source.getPaperId(), ignored -> new ArrayList<>()).add(source));
        return result;
    }

    private RagSource profileSource(PaperReference paper, PaperProfile profile) {
        RagSource source = new RagSource();
        source.setPaperId(profile.getPaperId());
        source.setPaperTitle(nullToDefault(paper.getTitle(), profile.getTitle()));
        source.setSourceType("paper_profile");
        source.setProfileId(profile.getId());
        source.setProfileVersion(profile.getProfileVersion());
        source.setContent(profile.getProfileText());
        source.setRetrievalRoute("hybrid_profile");
        return source;
    }

    private RagSource summarySource(PaperReference paper, PaperSectionSummary summary) {
        RagSource source = new RagSource();
        source.setPaperId(summary.getPaperId());
        source.setPaperTitle(paper.getTitle());
        source.setSourceType("section_summary");
        source.setSectionSummaryId(summary.getId());
        source.setSummaryVersion(summary.getSummaryVersion());
        source.setSectionId(summary.getSectionId());
        source.setSectionTitle(summary.getSectionTitle());
        source.setSectionType(summary.getSectionType());
        source.setContent(summary.getSummary());
        source.setRetrievalRoute("hybrid_summary");
        return source;
    }

    private RagSource rawChunkSource(RagSource rawSource) {
        rawSource.setSourceType("raw_chunk");
        rawSource.setRetrievalRoute(nullToDefault(rawSource.getRetrievalRoute(), "hybrid_raw"));
        return rawSource;
    }

    private List<Long> normalizePaperIds(List<Long> paperIds) {
        if (paperIds == null || paperIds.isEmpty()) {
            return List.of();
        }
        return paperIds.stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
    }

    private int positiveOrDefault(Integer value, int fallback) {
        return value != null && value > 0 ? value : fallback;
    }

    private int estimateTokens(String text) {
        return Math.max(1, (int) Math.ceil(nullToDefault(text, "").length() / 4.0));
    }

    private void appendLine(StringBuilder context, String label, String value) {
        if (value != null && !value.isBlank()) {
            context.append(label).append("：").append(clip(value, 1200)).append("\n");
        }
    }

    private String clip(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String normalized = value.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength) + "...";
    }

    private boolean containsAny(String value, String... keywords) {
        for (String keyword : keywords) {
            if (value.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String nullToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
