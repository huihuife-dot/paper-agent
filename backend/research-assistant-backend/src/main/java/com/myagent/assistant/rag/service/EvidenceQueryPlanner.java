package com.myagent.assistant.rag.service;

import com.myagent.assistant.rag.dto.EvidenceQueryPlan;

import java.util.List;

public interface EvidenceQueryPlanner {
    EvidenceQueryPlan plan(String question, List<Long> paperIds);
}
