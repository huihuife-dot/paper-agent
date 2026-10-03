package com.myagent.assistant.rag.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myagent.assistant.llm.LlmService;
import com.myagent.assistant.rag.dto.EvidenceQueryPlan;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

    @Test
    void keepsExplicitQuestionOnRuleFastPathEvenWhenModelRouterIsEnabled() {
        LlmService llm = mock(LlmService.class);
        when(llm.provider()).thenReturn("qwen");
        EvidenceQueryPlannerImpl hybrid = new EvidenceQueryPlannerImpl(llm, new ObjectMapper(), true, 0.60d);

        EvidenceQueryPlan plan = hybrid.plan("这篇论文使用了什么数据集？", List.of(7L));

        assertEquals("RULE", plan.getRouterSource());
        verify(llm, never()).generateAnswer(anyString());
    }

    @Test
    void modelAddsMultipleEvidenceTypesForComplexComparison() {
        LlmService llm = mock(LlmService.class);
        when(llm.provider()).thenReturn("qwen");
        when(llm.generateAnswer(anyString())).thenReturn("""
                {"intent":"METHOD_EFFECTIVENESS","knowledgeTypes":["METHOD","COMPARISON","RESULT"],
                 "sectionTypes":["METHOD","RESULT"],"confidence":0.91,
                 "explanation":"需要同时比较方法设计和实验结果"}
                """);
        EvidenceQueryPlannerImpl hybrid = new EvidenceQueryPlannerImpl(llm, new ObjectMapper(), true, 0.60d);

        EvidenceQueryPlan plan = hybrid.plan("为什么论文A的方法比论文B更有效？", List.of(7L, 8L));

        assertEquals("MULTI", plan.getScope());
        assertEquals("MODEL", plan.getRouterSource());
        assertEquals("METHOD_EFFECTIVENESS", plan.getIntent());
        assertTrue(plan.getTargetKnowledgeTypes().containsAll(List.of("METHOD", "COMPARISON", "RESULT")));
        assertEquals(0.91d, plan.getRouterConfidence());
    }

    @Test
    void invalidModelRouteFallsBackToRulePlan() {
        LlmService llm = mock(LlmService.class);
        when(llm.provider()).thenReturn("qwen");
        when(llm.generateAnswer(anyString())).thenReturn("not-json");
        EvidenceQueryPlannerImpl hybrid = new EvidenceQueryPlannerImpl(llm, new ObjectMapper(), true, 0.60d);

        EvidenceQueryPlan plan = hybrid.plan("这两个方法为什么不同？", List.of(7L, 8L));

        assertEquals("RULE_FALLBACK", plan.getRouterSource());
        assertTrue(plan.getRouterFallbackReason().contains("合法JSON"));
        assertEquals("MULTI", plan.getScope());
    }
}
