package com.myagent.assistant.rag.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 一轮问答实际执行的分层取证计划，供后端决策和前端解释使用。
 */
@Data
public class EvidenceQueryPlan {
    private String scope;
    private String intent;
    private String confidence;
    private String primaryLayer;
    private List<String> targetKnowledgeTypes = new ArrayList<>();
    private List<String> targetSectionTypes = new ArrayList<>();
    private List<Long> candidatePaperIds = new ArrayList<>();
    private Integer structuredSourceCount = 0;
    private Integer ragSourceCount = 0;
    private Boolean ragUsed = false;
    private String ragReason;
    private String explanation;
    /** RULE / MODEL / RULE_FALLBACK。 */
    private String routerSource = "RULE";
    private Double routerConfidence;
    private String routerFallbackReason;
    /** 每篇论文仍缺少的知识类型，用于解释章节摘要和 RAG 为什么被调用。 */
    private Map<Long, List<String>> missingKnowledgeTypes = new LinkedHashMap<>();
    private List<Long> sectionFallbackPaperIds = new ArrayList<>();
    private Boolean ragSectionFiltered = false;
}
