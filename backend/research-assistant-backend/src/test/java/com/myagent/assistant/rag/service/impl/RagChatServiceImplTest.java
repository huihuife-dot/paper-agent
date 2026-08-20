package com.myagent.assistant.rag.service.impl;

import com.myagent.assistant.chat.service.ChatHistoryService;
import com.myagent.assistant.llm.LlmService;
import com.myagent.assistant.rag.context.ContextStrategy;
import com.myagent.assistant.rag.context.FullTextContext;
import com.myagent.assistant.rag.context.HybridRagContext;
import com.myagent.assistant.rag.context.HybridRagContextRequest;
import com.myagent.assistant.rag.dto.RagChatRequest;
import com.myagent.assistant.rag.dto.RagChatResponse;
import com.myagent.assistant.rag.dto.RagSource;
import com.myagent.assistant.rag.dto.HistoryAwareQuery;
import com.myagent.assistant.rag.service.AdvancedQueryRewriteService;
import com.myagent.assistant.rag.service.ContextStrategyService;
import com.myagent.assistant.rag.service.FullTextContextService;
import com.myagent.assistant.rag.service.HybridRagContextService;
import com.myagent.assistant.rag.service.HistoryAwareQueryService;
import com.myagent.assistant.rag.service.IdeaSuggestionService;
import com.myagent.assistant.rag.service.PaperDiscoveryService;
import com.myagent.assistant.rag.service.RagPromptService;
import com.myagent.assistant.rag.service.RagRetrievalService;
import com.myagent.assistant.rag.service.RagStreamListener;
import com.myagent.assistant.rag.service.StructuredEvidenceService;
import com.myagent.assistant.rag.dto.RagStreamMetadata;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

class RagChatServiceImplTest {

    @Test
    void chatUsesHistoryAwareQuestionForRetrievalButSavesOriginalQuestion() {
        RagRetrievalService ragRetrievalService = mock(RagRetrievalService.class);
        RagPromptService ragPromptService = mock(RagPromptService.class);
        LlmService llmService = mock(LlmService.class);
        ChatHistoryService chatHistoryService = mock(ChatHistoryService.class);
        IdeaSuggestionService ideaSuggestionService = mock(IdeaSuggestionService.class);
        ContextStrategyService contextStrategyService = mock(ContextStrategyService.class);
        FullTextContextService fullTextContextService = mock(FullTextContextService.class);
        HybridRagContextService hybridRagContextService = mock(HybridRagContextService.class);
        AdvancedQueryRewriteService advancedQueryRewriteService = mock(AdvancedQueryRewriteService.class);
        PaperDiscoveryService paperDiscoveryService = mock(PaperDiscoveryService.class);
        HistoryAwareQueryService historyAwareQueryService = mock(HistoryAwareQueryService.class);
        StructuredEvidenceService structuredEvidenceService = mock(StructuredEvidenceService.class);
        RagSource source = new RagSource();
        source.setPaperId(3L);
        source.setContent("limitation evidence");
        List<RagSource> sources = List.of(source);
        String original = "它有什么局限？";
        String standalone = "论文3提出的方法有什么局限？";

        when(historyAwareQueryService.resolve(77L, original))
                .thenReturn(new HistoryAwareQuery(original, standalone, true, 4));
        when(contextStrategyService.chooseStrategy(List.of(3L))).thenReturn(ContextStrategy.VECTOR_RAG);
        when(ragRetrievalService.retrieveSources(standalone, 5, List.of(3L))).thenReturn(sources);
        when(ragPromptService.buildPrompt(standalone, sources)).thenReturn("standalone prompt");
        when(llmService.generateAnswer("standalone prompt")).thenReturn("局限回答");
        when(llmService.provider()).thenReturn("qwen");
        when(llmService.modelName()).thenReturn("qwen-plus");
        when(chatHistoryService.saveRagChat(77L, 3L, original, "局限回答", "qwen", "qwen-plus", sources))
                .thenReturn(77L);

        RagChatServiceImpl service = new RagChatServiceImpl(
                ragRetrievalService, ragPromptService, llmService, chatHistoryService,
                ideaSuggestionService, contextStrategyService, fullTextContextService,
                hybridRagContextService, advancedQueryRewriteService, paperDiscoveryService,
                historyAwareQueryService, structuredEvidenceService);
        RagChatRequest request = new RagChatRequest();
        request.setSessionId(77L);
        request.setPaperIds(List.of(3L));
        request.setQuestion(original);

        RagChatResponse response = service.chat(request);

        assertThat(response.getQuestion()).isEqualTo(original);
        assertThat(response.getRetrievalQuestion()).isEqualTo(standalone);
        assertThat(response.getAnswer()).isEqualTo("局限回答");
        verify(ragRetrievalService).retrieveSources(standalone, 5, List.of(3L));
        verify(chatHistoryService).saveRagChat(77L, 3L, original, "局限回答", "qwen", "qwen-plus", sources);
    }

    @Test
    void chatUsesSelectedPaperIdsAsRetrievalScope() {
        RagRetrievalService ragRetrievalService = mock(RagRetrievalService.class);
        RagPromptService ragPromptService = mock(RagPromptService.class);
        LlmService llmService = mock(LlmService.class);
        ChatHistoryService chatHistoryService = mock(ChatHistoryService.class);
        IdeaSuggestionService ideaSuggestionService = mock(IdeaSuggestionService.class);
        ContextStrategyService contextStrategyService = mock(ContextStrategyService.class);
        FullTextContextService fullTextContextService = mock(FullTextContextService.class);
        HybridRagContextService hybridRagContextService = mock(HybridRagContextService.class);
        AdvancedQueryRewriteService advancedQueryRewriteService = mock(AdvancedQueryRewriteService.class);
        PaperDiscoveryService paperDiscoveryService = mock(PaperDiscoveryService.class);
        HistoryAwareQueryService historyAwareQueryService = mock(HistoryAwareQueryService.class);
        StructuredEvidenceService structuredEvidenceService = mock(StructuredEvidenceService.class);

        RagSource source = new RagSource();
        source.setPaperId(3L);
        source.setChunkId(30L);
        source.setContent("This is a useful source chunk for token counting in the vector RAG branch.");
        List<RagSource> sources = List.of(source);

        when(contextStrategyService.chooseStrategy(List.of(3L, 5L))).thenReturn(ContextStrategy.VECTOR_RAG);
        when(historyAwareQueryService.resolve(null, "总结方法"))
                .thenReturn(new HistoryAwareQuery("总结方法", "总结方法", false, 0));
        when(ragRetrievalService.retrieveSources("总结方法", 5, List.of(3L, 5L))).thenReturn(sources);
        when(ragPromptService.buildPrompt("总结方法", sources)).thenReturn("prompt");
        when(llmService.generateAnswer("prompt")).thenReturn("answer");
        when(llmService.generateAnswerStream(eq("prompt"), any())).thenAnswer(invocation -> {
            Consumer<String> consumer = invocation.getArgument(1);
            consumer.accept("流式");
            consumer.accept("回答");
            return "流式回答";
        });
        when(llmService.provider()).thenReturn("test-provider");
        when(llmService.modelName()).thenReturn("test-model");
        when(chatHistoryService.saveRagChat(null, null, "总结方法", "answer", "test-provider", "test-model", sources))
                .thenReturn(9L);

        RagChatServiceImpl service = new RagChatServiceImpl(
                ragRetrievalService,
                ragPromptService,
                llmService,
                chatHistoryService,
                ideaSuggestionService,
                contextStrategyService,
                fullTextContextService,
                hybridRagContextService,
                advancedQueryRewriteService,
                paperDiscoveryService,
                historyAwareQueryService,
                structuredEvidenceService
        );

        RagChatRequest request = new RagChatRequest();
        request.setQuestion("总结方法");
        request.setTopK(5);
        request.setPaperId(99L);
        request.setPaperIds(Arrays.asList(3L, 5L, 3L, 0L, null));

        RagChatResponse response = service.chat(request);

        assertThat(response.getSessionId()).isEqualTo(9L);
        assertThat(response.getContextStrategy()).isEqualTo("VECTOR_RAG");
        assertThat(response.getContextTokenCount()).isGreaterThan(0);
        assertThat(response.getContextPaperIds()).containsExactly(3L, 5L);
        assertThat(response.getTiming()).isNotNull();
        assertThat(response.getTiming().getTotalMs()).isNotNegative();
        assertThat(response.getTiming().getDominantStage()).isNotBlank();
        List<String> deltas = new ArrayList<>();
        List<RagStreamMetadata> metadata = new ArrayList<>();
        RagChatResponse streamResponse = service.chatStream(request, new RagStreamListener() {
            @Override
            public void onMetadata(RagStreamMetadata value) {
                metadata.add(value);
            }

            @Override
            public void onDelta(String content) {
                deltas.add(content);
            }
        });
        assertThat(metadata).singleElement().extracting(RagStreamMetadata::getContextStrategy)
                .isEqualTo("VECTOR_RAG");
        assertThat(deltas).containsExactly("流式", "回答");
        assertThat(streamResponse.getAnswer()).isEqualTo("流式回答");
        assertThat(streamResponse.getTiming().getFirstTokenMs()).isNotNegative();
        assertThat(streamResponse.getTiming().getFirstContentMs()).isNotNegative();
        verify(ragRetrievalService, times(2)).retrieveSources("总结方法", 5, List.of(3L, 5L));
        verify(chatHistoryService).saveRagChat(null, null, "总结方法", "answer", "test-provider", "test-model", sources);
        verify(chatHistoryService).saveRagChat(null, null, "总结方法", "流式回答", "test-provider", "test-model", sources);
        verify(contextStrategyService, times(2)).chooseStrategy(List.of(3L, 5L));
        verify(fullTextContextService, never()).buildContext(any());
    }

    @Test
    void chatUsesFullTextContextWhenStrategySelectsFullText() {
        RagRetrievalService ragRetrievalService = mock(RagRetrievalService.class);
        RagPromptService ragPromptService = mock(RagPromptService.class);
        LlmService llmService = mock(LlmService.class);
        ChatHistoryService chatHistoryService = mock(ChatHistoryService.class);
        IdeaSuggestionService ideaSuggestionService = mock(IdeaSuggestionService.class);
        ContextStrategyService contextStrategyService = mock(ContextStrategyService.class);
        FullTextContextService fullTextContextService = mock(FullTextContextService.class);
        HybridRagContextService hybridRagContextService = mock(HybridRagContextService.class);
        AdvancedQueryRewriteService advancedQueryRewriteService = mock(AdvancedQueryRewriteService.class);
        PaperDiscoveryService paperDiscoveryService = mock(PaperDiscoveryService.class);
        HistoryAwareQueryService historyAwareQueryService = mock(HistoryAwareQueryService.class);
        StructuredEvidenceService structuredEvidenceService = mock(StructuredEvidenceService.class);

        RagSource source = new RagSource();
        source.setPaperId(7L);
        source.setChunkId(1L);
        source.setContent("method content");
        source.setRetrievalRoute("full_text");

        FullTextContext fullTextContext = new FullTextContext(
                7L,
                "Wind Paper",
                "[METHOD] Method\nmethod content",
                List.of(source),
                100
        );

        when(contextStrategyService.chooseStrategy(List.of(7L))).thenReturn(ContextStrategy.FULL_TEXT_PARSED);
        when(historyAwareQueryService.resolve(null, "这篇论文讲了什么？"))
                .thenReturn(new HistoryAwareQuery("这篇论文讲了什么？", "这篇论文讲了什么？", false, 0));
        when(fullTextContextService.buildContext(7L)).thenReturn(fullTextContext);
        when(ragPromptService.buildFullTextPrompt("这篇论文讲了什么？", fullTextContext)).thenReturn("full text prompt");
        when(llmService.generateAnswer("full text prompt")).thenReturn("全文回答");
        when(llmService.provider()).thenReturn("qwen");
        when(llmService.modelName()).thenReturn("qwen-plus");
        when(chatHistoryService.saveRagChat(null, 7L, "这篇论文讲了什么？", "全文回答", "qwen", "qwen-plus", List.of(source))).thenReturn(99L);
        when(ideaSuggestionService.shouldSuggestSaveAsIdea("这篇论文讲了什么？", "全文回答", List.of(source))).thenReturn(false);
        when(ideaSuggestionService.buildSuggestionReason("这篇论文讲了什么？", "全文回答", List.of(source))).thenReturn(null);

        RagChatServiceImpl service = new RagChatServiceImpl(
                ragRetrievalService,
                ragPromptService,
                llmService,
                chatHistoryService,
                ideaSuggestionService,
                contextStrategyService,
                fullTextContextService,
                hybridRagContextService,
                advancedQueryRewriteService,
                paperDiscoveryService,
                historyAwareQueryService,
                structuredEvidenceService
        );

        RagChatRequest request = new RagChatRequest();
        request.setQuestion("这篇论文讲了什么？");
        request.setPaperIds(List.of(7L));

        RagChatResponse response = service.chat(request);

        assertThat(response.getAnswer()).isEqualTo("全文回答");
        assertThat(response.getSources()).containsExactly(source);
        assertThat(response.getPrompt()).isEqualTo("full text prompt");
        assertThat(response.getContextStrategy()).isEqualTo("FULL_TEXT_PARSED");
        assertThat(response.getContextTokenCount()).isEqualTo(100);
        assertThat(response.getContextPaperIds()).containsExactly(7L);
        verify(ragRetrievalService, never()).retrieveSources(any(), any(), any());
    }

    @Test
    void chatUsesHybridContextWhenRawRetrievalFails() {
        RagRetrievalService ragRetrievalService = mock(RagRetrievalService.class);
        RagPromptService ragPromptService = mock(RagPromptService.class);
        LlmService llmService = mock(LlmService.class);
        ChatHistoryService chatHistoryService = mock(ChatHistoryService.class);
        IdeaSuggestionService ideaSuggestionService = mock(IdeaSuggestionService.class);
        ContextStrategyService contextStrategyService = mock(ContextStrategyService.class);
        FullTextContextService fullTextContextService = mock(FullTextContextService.class);
        HybridRagContextService hybridRagContextService = mock(HybridRagContextService.class);
        AdvancedQueryRewriteService advancedQueryRewriteService = mock(AdvancedQueryRewriteService.class);
        PaperDiscoveryService paperDiscoveryService = mock(PaperDiscoveryService.class);
        HistoryAwareQueryService historyAwareQueryService = mock(HistoryAwareQueryService.class);
        StructuredEvidenceService structuredEvidenceService = mock(StructuredEvidenceService.class);

        RagSource profileSource = new RagSource();
        profileSource.setPaperId(7L);
        profileSource.setSourceType("paper_profile");
        List<RagSource> hybridSources = List.of(profileSource);
        HybridRagContext hybridContext = new HybridRagContext(
                "[Paper 7] profile only context",
                hybridSources,
                120,
                List.of(7L, 8L)
        );

        when(contextStrategyService.chooseStrategy(List.of(7L, 8L))).thenReturn(ContextStrategy.HYBRID_RAG);
        when(historyAwareQueryService.resolve(null, "对比这两篇论文"))
                .thenReturn(new HistoryAwareQuery("对比这两篇论文", "对比这两篇论文", false, 0));
        when(ragRetrievalService.retrieveSources("对比这两篇论文", 5, List.of(7L, 8L)))
                .thenThrow(new RuntimeException("RAG 检索失败：Qdrant connection refused"));
        when(hybridRagContextService.buildContext(any(HybridRagContextRequest.class))).thenReturn(hybridContext);
        when(ragPromptService.buildHybridPrompt("对比这两篇论文", hybridContext)).thenReturn("hybrid prompt");
        when(llmService.generateAnswer("hybrid prompt")).thenReturn("hybrid answer");
        when(llmService.provider()).thenReturn("qwen");
        when(llmService.modelName()).thenReturn("qwen-plus");
        when(chatHistoryService.saveRagChat(null, null, "对比这两篇论文", "hybrid answer", "qwen", "qwen-plus", hybridSources)).thenReturn(124L);
        when(ideaSuggestionService.shouldSuggestSaveAsIdea("对比这两篇论文", "hybrid answer", hybridSources)).thenReturn(false);
        when(ideaSuggestionService.buildSuggestionReason("对比这两篇论文", "hybrid answer", hybridSources)).thenReturn(null);

        RagChatServiceImpl service = new RagChatServiceImpl(
                ragRetrievalService,
                ragPromptService,
                llmService,
                chatHistoryService,
                ideaSuggestionService,
                contextStrategyService,
                fullTextContextService,
                hybridRagContextService,
                advancedQueryRewriteService,
                paperDiscoveryService,
                historyAwareQueryService,
                structuredEvidenceService
        );

        RagChatRequest request = new RagChatRequest();
        request.setQuestion("对比这两篇论文");
        request.setPaperIds(List.of(7L, 8L));

        RagChatResponse response = service.chat(request);

        assertThat(response.getAnswer()).isEqualTo("hybrid answer");
        assertThat(response.getContextStrategy()).isEqualTo("HYBRID_RAG");
        assertThat(response.getSources()).containsExactly(profileSource);
    }

    @Test
    void chatUsesHybridContextWhenStrategySelectsHybridRag() {
        RagRetrievalService ragRetrievalService = mock(RagRetrievalService.class);
        RagPromptService ragPromptService = mock(RagPromptService.class);
        LlmService llmService = mock(LlmService.class);
        ChatHistoryService chatHistoryService = mock(ChatHistoryService.class);
        IdeaSuggestionService ideaSuggestionService = mock(IdeaSuggestionService.class);
        ContextStrategyService contextStrategyService = mock(ContextStrategyService.class);
        FullTextContextService fullTextContextService = mock(FullTextContextService.class);
        HybridRagContextService hybridRagContextService = mock(HybridRagContextService.class);
        AdvancedQueryRewriteService advancedQueryRewriteService = mock(AdvancedQueryRewriteService.class);
        PaperDiscoveryService paperDiscoveryService = mock(PaperDiscoveryService.class);
        HistoryAwareQueryService historyAwareQueryService = mock(HistoryAwareQueryService.class);
        StructuredEvidenceService structuredEvidenceService = mock(StructuredEvidenceService.class);

        RagSource rawSource = new RagSource();
        rawSource.setPaperId(7L);
        rawSource.setChunkId(70L);
        rawSource.setContent("raw method evidence");
        List<RagSource> rawSources = List.of(rawSource);

        RagSource profileSource = new RagSource();
        profileSource.setPaperId(7L);
        profileSource.setSourceType("paper_profile");
        RagSource summarySource = new RagSource();
        summarySource.setPaperId(8L);
        summarySource.setSourceType("section_summary");
        List<RagSource> hybridSources = List.of(profileSource, summarySource);

        HybridRagContext hybridContext = new HybridRagContext(
                "[Paper 7] A\n[Paper 8] B",
                hybridSources,
                300,
                List.of(7L, 8L)
        );

        when(contextStrategyService.chooseStrategy(List.of(7L, 8L))).thenReturn(ContextStrategy.HYBRID_RAG);
        when(historyAwareQueryService.resolve(null, "这两篇论文的方法有什么区别？"))
                .thenReturn(new HistoryAwareQuery(
                        "这两篇论文的方法有什么区别？",
                        "这两篇论文的方法有什么区别？",
                        false,
                        0));
        when(ragRetrievalService.retrieveSources("这两篇论文的方法有什么区别？", 5, List.of(7L, 8L))).thenReturn(rawSources);
        when(hybridRagContextService.buildContext(any(HybridRagContextRequest.class))).thenReturn(hybridContext);
        when(ragPromptService.buildHybridPrompt("这两篇论文的方法有什么区别？", hybridContext)).thenReturn("hybrid prompt");
        when(llmService.generateAnswer("hybrid prompt")).thenReturn("hybrid answer");
        when(llmService.provider()).thenReturn("qwen");
        when(llmService.modelName()).thenReturn("qwen-plus");
        when(chatHistoryService.saveRagChat(null, null, "这两篇论文的方法有什么区别？", "hybrid answer", "qwen", "qwen-plus", hybridSources)).thenReturn(123L);
        when(ideaSuggestionService.shouldSuggestSaveAsIdea("这两篇论文的方法有什么区别？", "hybrid answer", hybridSources)).thenReturn(false);
        when(ideaSuggestionService.buildSuggestionReason("这两篇论文的方法有什么区别？", "hybrid answer", hybridSources)).thenReturn(null);

        RagChatServiceImpl service = new RagChatServiceImpl(
                ragRetrievalService,
                ragPromptService,
                llmService,
                chatHistoryService,
                ideaSuggestionService,
                contextStrategyService,
                fullTextContextService,
                hybridRagContextService,
                advancedQueryRewriteService,
                paperDiscoveryService,
                historyAwareQueryService,
                structuredEvidenceService
        );

        RagChatRequest request = new RagChatRequest();
        request.setQuestion("这两篇论文的方法有什么区别？");
        request.setTopK(5);
        request.setPaperIds(List.of(7L, 8L));

        RagChatResponse response = service.chat(request);

        assertThat(response.getSessionId()).isEqualTo(123L);
        assertThat(response.getAnswer()).isEqualTo("hybrid answer");
        assertThat(response.getSources()).containsExactlyElementsOf(hybridSources);
        assertThat(response.getPrompt()).isEqualTo("hybrid prompt");
        assertThat(response.getContextStrategy()).isEqualTo("HYBRID_RAG");
        assertThat(response.getContextTokenCount()).isEqualTo(300);
        assertThat(response.getContextPaperIds()).containsExactly(7L, 8L);
        verify(ragRetrievalService).retrieveSources("这两篇论文的方法有什么区别？", 5, List.of(7L, 8L));
        verify(hybridRagContextService).buildContext(any(HybridRagContextRequest.class));
        verify(ragPromptService).buildHybridPrompt("这两篇论文的方法有什么区别？", hybridContext);
        verify(ragPromptService, never()).buildPrompt(any(), any());
        verify(ragPromptService, never()).buildFullTextPrompt(any(), any());
        verify(fullTextContextService, never()).buildContext(any());
    }
}
