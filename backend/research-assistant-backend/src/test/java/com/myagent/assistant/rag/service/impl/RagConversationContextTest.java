package com.myagent.assistant.rag.service.impl;

import com.myagent.assistant.chat.context.ConversationContextService;
import com.myagent.assistant.chat.entity.ChatMessage;
import com.myagent.assistant.chat.service.ChatHistoryService;
import com.myagent.assistant.experiment.ExperimentTrace;
import com.myagent.assistant.llm.LlmMessage;
import com.myagent.assistant.llm.LlmService;
import com.myagent.assistant.rag.context.ContextStrategy;
import com.myagent.assistant.rag.context.FullTextContext;
import com.myagent.assistant.rag.context.HybridRagContext;
import com.myagent.assistant.rag.context.StructuredEvidenceContext;
import com.myagent.assistant.rag.dto.*;
import com.myagent.assistant.rag.service.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 编排测试：真实上下文组装＋模拟检索/模型，不访问外部模型或数据库。 */
class RagConversationContextTest {
    private final ChatHistoryService history = mock(ChatHistoryService.class);
    private final LlmService llm = mock(LlmService.class);
    private final HistoryAwareQueryService rewrite = mock(HistoryAwareQueryService.class);
    private final RagRetrievalService retrieval = mock(RagRetrievalService.class);
    private final RagPromptService prompts = mock(RagPromptService.class);
    private final ContextStrategyService strategy = mock(ContextStrategyService.class);
    private final StructuredEvidenceService structured = mock(StructuredEvidenceService.class);
    private final FullTextContextService fullText = mock(FullTextContextService.class);
    private final HybridRagContextService hybrid = mock(HybridRagContextService.class);
    private final AdvancedQueryRewriteService advanced = mock(AdvancedQueryRewriteService.class);
    private final String original = "它有什么局限？用中文表格回答";
    private final String standalone = "论文3提出的方法有什么局限？";

    private RagChatServiceImpl service(boolean enabled, int window) {
        return new RagChatServiceImpl(retrieval, prompts, llm, history, mock(IdeaSuggestionService.class), strategy,
                fullText, hybrid, advanced,
                mock(PaperDiscoveryService.class), rewrite, structured,
                new ConversationContextService(history, enabled, window, 256, 128, 80));
    }

    private RagChatRequest request() {
        RagChatRequest request = new RagChatRequest();
        request.setSessionId(77L); request.setPaperIds(List.of(3L)); request.setQuestion(original);
        return request;
    }

    private void setup() {
        ChatMessage user = new ChatMessage(); user.setSessionId(77L); user.setRole("user"); user.setContent("介绍论文3的方法");
        ChatMessage answer = new ChatMessage(); answer.setSessionId(77L); answer.setRole("assistant");
        answer.setContent("方法说明\n" + "完整表格原文\n".repeat(80));
        when(history.listRecentMessages(77L, 81)).thenReturn(List.of(user, answer));
        when(rewrite.resolveWithHistory(eq(77L), eq(original), anyList()))
                .thenReturn(new HistoryAwareQuery(original, standalone, true, 2));
        when(rewrite.resolve(77L, original)).thenReturn(new HistoryAwareQuery(original, standalone, true, 2));
        when(strategy.chooseStrategy(List.of(3L))).thenReturn(ContextStrategy.VECTOR_RAG);
        when(retrieval.retrieveSources(standalone, 5, List.of(3L))).thenReturn(List.of());
        when(prompts.buildPrompt(original, List.of())).thenReturn("本轮证据提示词");
        when(prompts.buildPrompt(standalone, List.of())).thenReturn("旧版提示词");
        when(llm.generateMessages(anyList())).thenReturn("新回答");
        when(llm.generateAnswer("旧版提示词")).thenReturn("旧版回答");
        when(llm.provider()).thenReturn("test"); when(llm.modelName()).thenReturn("test-model");
    }

    @Test
    void retrievalUsesStandaloneQuestionButAnswerUsesOriginalAndWholeHistory() {
        setup();
        var response = service(true, 32768).chat(request());
        ArgumentCaptor<List<LlmMessage>> input = ArgumentCaptor.forClass(List.class);
        verify(llm).generateMessages(input.capture());
        assertThat(input.getValue()).extracting(LlmMessage::role).containsExactly("system", "user", "assistant", "user");
        assertThat(input.getValue().get(2).content()).isEqualTo("方法说明\n" + "完整表格原文\n".repeat(80));
        assertThat(input.getValue().getLast().content()).contains(original, "本轮证据提示词");
        assertThat(response.getConversationContext().historyMessageCount()).isEqualTo(2);
        assertThat(response.getRetrievalQuestion()).isEqualTo(standalone);
        verify(retrieval).retrieveSources(standalone, 5, List.of(3L));
        verify(history).listRecentMessages(77L, 81);
        verify(history, never()).listMessages(any());
        verify(rewrite, never()).resolve(any(), anyString());
        verify(llm, never()).generateAnswer(anyString());
        verify(history).saveRagChat(77L, 3L, original, "新回答", "test", "test-model", List.of());
    }

    @Test
    void normalAndStreamingUseIdenticalMessagePayloadAndExposeSameBudget() {
        setup();
        when(llm.generateMessagesStream(anyList(), any())).thenAnswer(call -> {
            Consumer<String> delta = call.getArgument(1); delta.accept("新"); delta.accept("回答"); return "新回答";
        });
        var service = service(true, 32768);
        var normal = service.chat(request());
        var listener = mock(RagStreamListener.class);
        var streamed = service.chatStream(request(), listener);
        ArgumentCaptor<List<LlmMessage>> normalInput = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<List<LlmMessage>> streamInput = ArgumentCaptor.forClass(List.class);
        verify(llm).generateMessages(normalInput.capture());
        verify(llm).generateMessagesStream(streamInput.capture(), any());
        assertThat(streamInput.getValue()).isEqualTo(normalInput.getValue());
        assertThat(streamed.getConversationContext()).isEqualTo(normal.getConversationContext());
        ArgumentCaptor<RagStreamMetadata> metadata = ArgumentCaptor.forClass(RagStreamMetadata.class);
        verify(listener).onMetadata(metadata.capture());
        assertThat(metadata.getValue().getConversationContext()).isEqualTo(normal.getConversationContext());
        verify(listener).onDelta("新"); verify(listener).onDelta("回答");
        verify(llm, never()).generateAnswerStream(anyString(), any());
    }

    @Test
    void budgetFailureMakesNoAnswerRequestAndSavesNoHalfTurn() {
        setup();
        when(prompts.buildPrompt(original, List.of())).thenReturn("过长证据".repeat(3000));
        assertThatThrownBy(() -> service(true, 4096).chat(request())).hasMessageContaining("超过对话输入预算");
        verify(llm, never()).generateMessages(anyList());
        verify(llm, never()).generateAnswer(anyString());
        verify(history, never()).saveRagChat(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void disabledFlagRestoresOldSinglePromptPath() {
        setup();
        var response = service(false, 32768).chat(request());
        assertThat(response.getAnswer()).isEqualTo("旧版回答");
        assertThat(response.getConversationContext()).isNull();
        verify(history, never()).listRecentMessages(any(), anyInt());
        verify(llm, never()).generateMessages(anyList());
        verify(rewrite).resolve(77L, original);
    }

    @Test
    void experimentKeepsOldBaselineEvenWhenFeatureEnabled() {
        setup();
        try (var ignored = ExperimentTrace.open(ExperimentTrace.Variant.BASELINE)) {
            var response = service(true, 32768).chat(request());
            assertThat(response.getAnswer()).isEqualTo("旧版回答");
            assertThat(response.getConversationContext()).isNull();
            verifyNoInteractions(history);
            verify(llm, never()).generateMessages(anyList());
        }
    }

    @Test
    void structuredFirstAlsoUsesOriginalQuestionAndRecentHistory() {
        setup();
        var context = new StructuredEvidenceContext();
        context.setHandled(true); context.setContextStrategy("STRUCTURED_FIRST_SINGLE"); context.setPaperIds(List.of(3L));
        context.setTokenCount(100); context.setContextText("知识单元证据");
        when(structured.build(standalone, List.of(3L), 5)).thenReturn(context);
        when(prompts.buildStructuredPrompt(original, context)).thenReturn("结构化证据提示词");
        var response = service(true, 32768).chat(request());
        assertThat(response.getContextStrategy()).isEqualTo("STRUCTURED_FIRST_SINGLE");
        assertThat(response.getConversationContext().historyMessageCount()).isEqualTo(2);
        verify(prompts).buildStructuredPrompt(original, context);
        verifyNoInteractions(strategy, retrieval);
    }

    @ParameterizedTest
    @EnumSource(value = ContextStrategy.class, names = {"FULL_TEXT_PARSED", "HYBRID_RAG", "LIBRARY_DISCOVERY"})
    void remainingFallbackBranchesKeepRetrievalQuestionSeparateFromAnswerRequest(ContextStrategy selected) {
        setup();
        when(strategy.chooseStrategy(List.of(3L))).thenReturn(selected);
        var full = new FullTextContext(3L, "论文3", "全文", List.of(), 10);
        var mixed = new HybridRagContext("混合证据", List.of(), 10, List.of(3L));
        when(fullText.buildContext(3L)).thenReturn(full);
        when(prompts.buildFullTextPrompt(original, full)).thenReturn("全文提示词");
        when(hybrid.buildContext(any())).thenReturn(mixed);
        when(prompts.buildHybridPrompt(original, mixed)).thenReturn("混合提示词");
        when(prompts.buildLibraryDiscoveryPrompt(eq(original), anyList())).thenReturn("全库提示词");
        var response = service(true, 32768).chat(request());
        assertThat(response.getConversationContext().historyMessageCount()).isEqualTo(2);
        assertThat(response.getContextStrategy()).isEqualTo(selected.name());
        assertThat(response.getRetrievalQuestion()).isEqualTo(standalone);
        switch (selected) {
            case FULL_TEXT_PARSED -> verify(prompts).buildFullTextPrompt(original, full);
            case HYBRID_RAG -> verify(prompts).buildHybridPrompt(original, mixed);
            case LIBRARY_DISCOVERY -> {
                verify(advanced).rewrite(standalone);
                verify(prompts).buildLibraryDiscoveryPrompt(eq(original), anyList());
            }
            default -> throw new AssertionError(selected);
        }
    }

    @Test
    void failedAnswerIsNotPersistedAsSuccessfulHistory() {
        setup();
        when(llm.generateMessages(anyList())).thenThrow(new IllegalStateException("模拟模型失败"));
        assertThatThrownBy(() -> service(true, 32768).chat(request())).hasMessage("模拟模型失败");
        verify(history, never()).saveRagChat(any(), any(), any(), any(), any(), any(), any());
    }
}
