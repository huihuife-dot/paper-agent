package com.myagent.assistant.experiment;

import com.myagent.assistant.embedding.EmbeddingService;
import com.myagent.assistant.llm.LlmService;
import com.myagent.assistant.rag.dto.*;
import com.myagent.assistant.rag.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RoutingExperimentControllerTest {
    static final String TOKEN = "test-only-token-at-least-24-characters";
    EvidenceQueryPlanner planner = mock(EvidenceQueryPlanner.class);
    RagChatService chat = mock(RagChatService.class);
    RoutingExperimentController controller(String token) {
        return new RoutingExperimentController(token, planner, chat, mock(LlmService.class),
                mock(EmbeddingService.class), mock(JevRouteClient.class), new MockEnvironment());
    }
    RoutingExperimentController.Request request(String mode) {
        var input = new RagChatRequest(); input.setQuestion("问题");
        return new RoutingExperimentController.Request("BASELINE", mode, input);
    }
    @Test void rejectsUnauthorizedAndUnconfiguredToken() {
        assertEquals(401, assertThrows(ResponseStatusException.class, () -> controller(TOKEN).run("wrong", request("route"))).getStatusCode().value());
        assertEquals(503, assertThrows(ResponseStatusException.class, () -> controller("").run("", request("route"))).getStatusCode().value());
        verifyNoInteractions(planner, chat);
    }
    @Test void endpointAbsentByDefault() {
        new ApplicationContextRunner().withUserConfiguration(RoutingExperimentController.class).run(context -> {
            assertNull(context.getStartupFailure());
            assertEquals(0, context.getBeansOfType(RoutingExperimentController.class).size());
        });
    }
    @Test void enabledEndpointWiresPrimaryDecoratorWithoutChangingBaselineBean() {
        new ApplicationContextRunner()
                .withPropertyValues("rag.experiment.enabled=true", "rag.experiment.token=" + TOKEN)
                .withBean(com.fasterxml.jackson.databind.ObjectMapper.class, com.fasterxml.jackson.databind.ObjectMapper::new)
                .withBean(LlmService.class, () -> mock(LlmService.class))
                .withBean(EmbeddingService.class, () -> mock(EmbeddingService.class))
                .withBean(RagChatService.class, () -> chat)
                .withUserConfiguration(RoutingExperimentController.class, JevRouteClient.class,
                        ExperimentalEvidenceQueryPlanner.class,
                        com.myagent.assistant.rag.service.impl.EvidenceQueryPlannerImpl.class)
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    assertInstanceOf(ExperimentalEvidenceQueryPlanner.class, context.getBean(EvidenceQueryPlanner.class));
                    assertNotNull(context.getBean(com.myagent.assistant.rag.service.impl.EvidenceQueryPlannerImpl.class));
                    assertNotNull(context.getBean(RoutingExperimentController.class));
                });
    }
    @Test void rejectsHistoryAndInvalidModesBeforeAnyCalls() {
        var input = request("chat"); input.request().setSessionId(1L);
        assertThrows(ResponseStatusException.class, () -> controller(TOKEN).run(TOKEN, input));
        assertThrows(ResponseStatusException.class, () -> controller(TOKEN).run(TOKEN, request("bad")));
        verifyNoInteractions(planner, chat);
    }
    @Test void failureEnvelopeKeepsUsageAndClearsThread() {
        when(planner.plan(anyString(), anyList())).thenAnswer(invocation -> {
            ExperimentTrace.startCall("qwen", "model", "generation");
            throw new IllegalStateException("secret response must not be exposed");
        });
        var response = controller(TOKEN).run(TOKEN, request("route"));
        assertFalse(response.success()); assertEquals("IllegalStateException", response.error());
        assertEquals(1, response.calls().size()); assertNull(response.calls().getFirst().inputTokens);
        assertFalse(ExperimentTrace.active());
    }
    @Test void executesRequestedModeOnly() {
        when(chat.chat(any())).thenReturn(new RagChatResponse());
        assertTrue(controller(TOKEN).run(TOKEN, request("chat")).success());
        verify(chat).chat(any()); verifyNoInteractions(planner);
    }
}
