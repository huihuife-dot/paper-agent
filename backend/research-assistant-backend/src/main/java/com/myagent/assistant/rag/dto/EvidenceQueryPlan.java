package com.myagent.assistant.rag.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

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
}
