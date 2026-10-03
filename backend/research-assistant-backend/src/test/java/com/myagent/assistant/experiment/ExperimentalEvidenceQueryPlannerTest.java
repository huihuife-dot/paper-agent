package com.myagent.assistant.experiment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myagent.assistant.llm.LlmService;
import com.myagent.assistant.rag.dto.ModelEvidenceRoute;
import com.myagent.assistant.rag.service.impl.EvidenceQueryPlannerImpl;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExperimentalEvidenceQueryPlannerTest {
    LlmService llm = mock(LlmService.class);
    JevRouteClient jev = mock(JevRouteClient.class);
    EvidenceQueryPlannerImpl baseline() {
        when(llm.provider()).thenReturn("qwen");
        when(llm.generateAnswer(anyString())).thenReturn("{\"knowledgeTypes\":[\"METHOD\"],\"sectionTypes\":[\"METHOD\"],\"confidence\":0.9}");
        return new EvidenceQueryPlannerImpl(llm, new ObjectMapper(), true, 0.6);
    }
    @Test void normalRequestDelegatesWithoutJev() {
        var original = baseline(); var router = new ExperimentalEvidenceQueryPlanner(original, jev);
        assertEquals(original.plan("为什么有效", List.of(7L)), router.plan("为什么有效", List.of(7L)));
        verifyNoInteractions(jev); assertFalse(ExperimentTrace.active());
    }
    @Test void simpleQuestionRemainsRuleOnlyInBothVariants() {
        var router = new ExperimentalEvidenceQueryPlanner(baseline(), jev);
        for (var variant : ExperimentTrace.Variant.values()) try (var trace = ExperimentTrace.open(variant)) {
            assertEquals("RULE", router.plan("使用什么数据集？", List.of(7L)).getRouterSource());
        }
        verifyNoInteractions(jev); verify(llm, never()).generateAnswer(anyString());
    }
    @Test void jevOnlyChangesModelDecisionAndPreservesRuleUnionAndScope() {
        var router = new ExperimentalEvidenceQueryPlanner(baseline(), jev);
        ModelEvidenceRoute route = new ModelEvidenceRoute(); route.setIntent("METHOD");
        route.setConfidence(0.91); route.setKnowledgeTypes(List.of("RESULT")); route.setSectionTypes(List.of("RESULT"));
        when(jev.route(anyString(), any())).thenReturn(route);
        try (var trace = ExperimentTrace.open(ExperimentTrace.Variant.JEV)) {
            var result = router.plan("这个方法为什么有效？", List.of(7L, 8L));
            assertEquals("MULTI", result.getScope()); assertEquals("JEV", result.getRouterSource());
            assertTrue(result.getTargetKnowledgeTypes().containsAll(List.of("METHOD", "RESULT")));
            assertSame(result, trace.plan); verify(llm, never()).generateAnswer(anyString());
        }
    }
    @Test void fallbackIsNotDisguisedAsJevSuccessAndTraceIsCleared() {
        var router = new ExperimentalEvidenceQueryPlanner(baseline(), jev);
        when(jev.route(anyString(), any())).thenThrow(new IllegalStateException("JEV_UNCERTAIN"));
        try (var trace = ExperimentTrace.open(ExperimentTrace.Variant.JEV)) {
            assertEquals("MODEL", router.plan("为什么有效", List.of(7L)).getRouterSource());
            assertEquals(List.of("JEV_UNCERTAIN"), trace.fallbacks);
        }
        assertFalse(ExperimentTrace.active());
        try (var trace = ExperimentTrace.open(ExperimentTrace.Variant.BASELINE)) { assertTrue(trace.fallbacks.isEmpty()); }
    }
}
