package com.myagent.assistant.rag.service.impl;

import com.myagent.assistant.rag.dto.EvidenceQueryPlan;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EvidenceQueryPlannerImplTest {

    private final EvidenceQueryPlannerImpl planner = new EvidenceQueryPlannerImpl();

    @Test
    void routesSingleDatasetQuestionToKnowledgeUnits() {
        EvidenceQueryPlan plan = planner.plan("这篇论文使用了什么数据集？", List.of(7L));

        assertEquals("SINGLE", plan.getScope());
        assertEquals("DATASET", plan.getIntent());
        assertEquals("KNOWLEDGE_UNIT", plan.getPrimaryLayer());
        assertEquals("HIGH", plan.getConfidence());
        assertTrue(plan.getTargetKnowledgeTypes().contains("DATASET"));
    }

    @Test
    void routesMultiMethodComparisonWithoutDefaultingToFuzzyRag() {
        EvidenceQueryPlan plan = planner.plan("比较这两篇论文使用的方法", List.of(7L, 8L));

        assertEquals("MULTI", plan.getScope());
        assertEquals("METHOD", plan.getIntent());
        assertEquals("HIGH", plan.getConfidence());
        assertTrue(plan.getTargetSectionTypes().contains("METHOD"));
    }

    @Test
    void routesLibraryTrendQuestionToCatalog() {
        EvidenceQueryPlan plan = planner.plan("近年来风电预测有哪些研究方向？", List.of());

        assertEquals("LIBRARY", plan.getScope());
        assertEquals("DISCOVERY", plan.getIntent());
        assertEquals("PAPER_CATALOG", plan.getPrimaryLayer());
    }

    @Test
    void marksSubjectiveQuestionAsLowConfidence() {
        EvidenceQueryPlan plan = planner.plan("有哪些比较新颖的空间建模方向？", List.of());

        assertEquals("FUZZY", plan.getIntent());
        assertEquals("LOW", plan.getConfidence());
    }
}
