package com.myagent.assistant.rag.service.impl;

import com.myagent.assistant.chat.service.ChatHistoryService;
import com.myagent.assistant.chat.context.ConversationContextService;
import org.springframework.beans.factory.annotation.Autowired;
import com.myagent.assistant.llm.LlmService;
import com.myagent.assistant.experiment.ExperimentTrace;
import com.myagent.assistant.observability.RagTimingTrace;
import com.myagent.assistant.rag.context.ContextStrategy;
import com.myagent.assistant.rag.context.FullTextContext;
import com.myagent.assistant.rag.context.HybridRagContext;
import com.myagent.assistant.rag.context.HybridRagContextRequest;
import com.myagent.assistant.rag.context.StructuredEvidenceContext;
import com.myagent.assistant.rag.dto.EvidenceQueryPlan;
import com.myagent.assistant.rag.dto.PaperRelevance;
import com.myagent.assistant.rag.dto.HistoryAwareQuery;
import com.myagent.assistant.rag.dto.QueryRewriteResult;
import com.myagent.assistant.rag.dto.RagChatRequest;
import com.myagent.assistant.rag.dto.RagChatResponse;
import com.myagent.assistant.rag.dto.RagSource;
import com.myagent.assistant.rag.dto.RagStreamMetadata;
import com.myagent.assistant.rag.dto.RagTiming;
import com.myagent.assistant.rag.service.AdvancedQueryRewriteService;
import com.myagent.assistant.rag.service.ContextStrategyService;
import com.myagent.assistant.rag.service.FullTextContextService;
import com.myagent.assistant.rag.service.HistoryAwareQueryService;
import com.myagent.assistant.rag.service.HybridRagContextService;
import com.myagent.assistant.rag.service.IdeaSuggestionService;
import com.myagent.assistant.rag.service.PaperDiscoveryService;
import com.myagent.assistant.rag.service.RagChatService;
import com.myagent.assistant.rag.service.RagPromptService;
import com.myagent.assistant.rag.service.RagRetrievalService;
import com.myagent.assistant.rag.service.RagStreamListener;
import com.myagent.assistant.rag.service.StructuredEvidenceService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * RAG 问答服务实现。
 *
 * 当前阶段支持两种上下文策略：
 * - VECTOR_RAG：问题 embedding -> Qdrant topK -> chunk prompt。
 * - FULL_TEXT_PARSED：单篇论文在 token 预算内时，按章节组织正文上下文。
 */
@Service
public class RagChatServiceImpl implements RagChatService {


    private final RagPromptService ragPromptService;

    private final RagRetrievalService ragRetrievalService;

    private final LlmService llmService;

    private final ChatHistoryService chatHistoryService;

    private final IdeaSuggestionService ideaSuggestionService;

    private final ContextStrategyService contextStrategyService;

    private final FullTextContextService fullTextContextService;

    private final HybridRagContextService hybridRagContextService;

    private final AdvancedQueryRewriteService advancedQueryRewriteService;

    private final PaperDiscoveryService paperDiscoveryService;

    private final HistoryAwareQueryService historyAwareQueryService;

    private final StructuredEvidenceService structuredEvidenceService;

    private final ConversationContextService conversationContextService;

    public RagChatServiceImpl(RagRetrievalService ragRetrievalService,
                              RagPromptService ragPromptService,
                              LlmService llmService,
                              ChatHistoryService chatHistoryService,
                              IdeaSuggestionService ideaSuggestionService,
                              ContextStrategyService contextStrategyService,
                              FullTextContextService fullTextContextService,
                              HybridRagContextService hybridRagContextService,
                              AdvancedQueryRewriteService advancedQueryRewriteService,
                              PaperDiscoveryService paperDiscoveryService,
                              HistoryAwareQueryService historyAwareQueryService,
                              StructuredEvidenceService structuredEvidenceService) {
        this(ragRetrievalService, ragPromptService, llmService, chatHistoryService, ideaSuggestionService,
                contextStrategyService, fullTextContextService, hybridRagContextService, advancedQueryRewriteService,
                paperDiscoveryService, historyAwareQueryService, structuredEvidenceService, null);
    }

    /** Spring 使用完整构造器；旧构造器保留给既有离线调用/测试，不改变其基线语义。 */
    @Autowired
    public RagChatServiceImpl(RagRetrievalService ragRetrievalService, RagPromptService ragPromptService,
            LlmService llmService, ChatHistoryService chatHistoryService, IdeaSuggestionService ideaSuggestionService,
            ContextStrategyService contextStrategyService, FullTextContextService fullTextContextService,
            HybridRagContextService hybridRagContextService, AdvancedQueryRewriteService advancedQueryRewriteService,
            PaperDiscoveryService paperDiscoveryService, HistoryAwareQueryService historyAwareQueryService,
            StructuredEvidenceService structuredEvidenceService, ConversationContextService conversationContextService) {
        this.ragRetrievalService = ragRetrievalService;
        this.ragPromptService = ragPromptService;
        this.llmService = llmService;
        this.chatHistoryService = chatHistoryService;
        this.ideaSuggestionService = ideaSuggestionService;
        this.contextStrategyService = contextStrategyService;
        this.fullTextContextService = fullTextContextService;
        this.hybridRagContextService = hybridRagContextService;
        this.advancedQueryRewriteService = advancedQueryRewriteService;
        this.paperDiscoveryService = paperDiscoveryService;
        this.historyAwareQueryService = historyAwareQueryService;
        this.structuredEvidenceService = structuredEvidenceService;
        this.conversationContextService = conversationContextService;
    }

    @Override
    public RagChatResponse chat(RagChatRequest request) {
        return executeChat(request, null);
    }

    @Override
    public RagChatResponse chatStream(RagChatRequest request, RagStreamListener listener) {
        if (listener == null) {
            throw new RuntimeException("流式监听器不能为空");
        }
        return executeChat(request, listener);
    }

    private RagChatResponse executeChat(RagChatRequest request, RagStreamListener streamListener) {
        if (request == null || request.getQuestion() == null || request.getQuestion().isBlank()) {
            throw new RuntimeException("问题不能为空");
        }

        long totalStartedAt = RagTimingTrace.start();
        RagTimingTrace.begin();
        try {
        int topK = request.getTopK() != null && request.getTopK() > 0 ? request.getTopK() : 5;
        // 独立实验保持既有单轮基线；普通聊天开关关闭时也能原样回滚。
        boolean withConversation = conversationContextService != null && conversationContextService.isEnabled()
                && !ExperimentTrace.active();

        long historyRewriteStartedAt = RagTimingTrace.start();
        HistoryAwareQuery historyAwareQuery;
        ConversationContextService.Snapshot historySnapshot;
        try (var usageStage = ExperimentTrace.stage("historyRewrite")) {
            historySnapshot = withConversation ? conversationContextService.snapshot(request.getSessionId()) : null;
            historyAwareQuery = withConversation
                    ? historyAwareQueryService.resolveWithHistory(request.getSessionId(), request.getQuestion(), historySnapshot.messages())
                    : historyAwareQueryService.resolve(request.getSessionId(), request.getQuestion());
        } finally {
            RagTimingTrace.addElapsed(RagTimingTrace.QUERY_REWRITE, historyRewriteStartedAt);
        }
        String retrievalQuestion = historyAwareQuery.retrievalQuestion();
        String answerQuestion = withConversation ? request.getQuestion() : retrievalQuestion;

        // 1. 解析本轮有效文献范围，并选择上下文策略。
        long strategyStartedAt = RagTimingTrace.start();
        List<Long> effectivePaperIds;
        ContextStrategy contextStrategy = null;
        try {
            effectivePaperIds = resolvePaperIds(request);
        } finally {
            RagTimingTrace.addElapsed(RagTimingTrace.STRATEGY_SELECTION, strategyStartedAt);
        }

        List<RagSource> sources;
        String prompt;
        Integer contextTokenCount;
        List<Long> contextPaperIds;
        String contextStrategyName;
        EvidenceQueryPlan evidencePlan = null;

        StructuredEvidenceContext structuredContext;
        long structuredStartedAt = RagTimingTrace.start();
        try {
            structuredContext = structuredEvidenceService.build(retrievalQuestion, effectivePaperIds, topK);
        } finally {
            RagTimingTrace.addElapsed(RagTimingTrace.CONTEXT_BUILD, structuredStartedAt);
        }

        if (structuredContext == null || !structuredContext.isHandled()) {
            long fallbackStrategyStartedAt = RagTimingTrace.start();
            try {
                contextStrategy = contextStrategyService.chooseStrategy(effectivePaperIds);
            } finally {
                RagTimingTrace.addElapsed(RagTimingTrace.STRATEGY_SELECTION, fallbackStrategyStartedAt);
            }
        }

        if (structuredContext != null && structuredContext.isHandled()) {
            sources = structuredContext.getSources();
            long promptStartedAt = RagTimingTrace.start();
            try {
                prompt = ragPromptService.buildStructuredPrompt(answerQuestion, structuredContext);
            } finally {
                RagTimingTrace.addElapsed(RagTimingTrace.PROMPT_BUILD, promptStartedAt);
            }
            contextTokenCount = structuredContext.getTokenCount();
            contextPaperIds = structuredContext.getPaperIds();
            contextStrategyName = structuredContext.getContextStrategy();
            evidencePlan = structuredContext.getPlan();
        } else if (ContextStrategy.FULL_TEXT_PARSED.equals(contextStrategy)) {
            Long paperId = effectivePaperIds.get(0);
            long contextStartedAt = RagTimingTrace.start();
            FullTextContext fullTextContext;
            try {
                fullTextContext = fullTextContextService.buildContext(paperId);
            } finally {
                RagTimingTrace.addElapsed(RagTimingTrace.CONTEXT_BUILD, contextStartedAt);
            }
            sources = fullTextContext.getSources();
            long promptStartedAt = RagTimingTrace.start();
            try {
                prompt = ragPromptService.buildFullTextPrompt(answerQuestion, fullTextContext);
            } finally {
                RagTimingTrace.addElapsed(RagTimingTrace.PROMPT_BUILD, promptStartedAt);
            }
            contextTokenCount = fullTextContext.getTokenCount();
            contextPaperIds = List.of(paperId);
            contextStrategyName = contextStrategy.name();
        } else if (ContextStrategy.HYBRID_RAG.equals(contextStrategy)) {
            List<RagSource> rawSources = retrieveHybridRawSources(retrievalQuestion, topK, effectivePaperIds);
            long contextStartedAt = RagTimingTrace.start();
            HybridRagContext hybridContext;
            try {
                hybridContext = hybridRagContextService.buildContext(new HybridRagContextRequest(
                        retrievalQuestion,
                        effectivePaperIds,
                        rawSources,
                        4,
                        2
                ));
            } finally {
                RagTimingTrace.addElapsed(RagTimingTrace.CONTEXT_BUILD, contextStartedAt);
            }
            sources = hybridContext.getSources();
            long promptStartedAt = RagTimingTrace.start();
            try {
                prompt = ragPromptService.buildHybridPrompt(answerQuestion, hybridContext);
            } finally {
                RagTimingTrace.addElapsed(RagTimingTrace.PROMPT_BUILD, promptStartedAt);
            }
            contextTokenCount = hybridContext.getTokenCount();
            contextPaperIds = hybridContext.getPaperIds();
            contextStrategyName = contextStrategy.name();
        } else if (ContextStrategy.LIBRARY_DISCOVERY.equals(contextStrategy)) {
            // 全库文献发现：高级 Query Rewrite → 多路检索 + RRF 融合 → 论文聚合
            long rewriteStartedAt = RagTimingTrace.start();
            QueryRewriteResult rewriteResult;
            try {
                rewriteResult = advancedQueryRewriteService.rewrite(retrievalQuestion);
            } finally {
                RagTimingTrace.addElapsed(RagTimingTrace.QUERY_REWRITE, rewriteStartedAt);
            }
            sources = ragRetrievalService.retrieveSourcesWithRewrite(rewriteResult, topK, effectivePaperIds);
            long promptStartedAt = RagTimingTrace.start();
            try {
                prompt = ragPromptService.buildLibraryDiscoveryPrompt(answerQuestion, sources);
            } finally {
                RagTimingTrace.addElapsed(RagTimingTrace.PROMPT_BUILD, promptStartedAt);
            }
            contextTokenCount = estimateSourceTokens(sources);
            contextPaperIds = effectivePaperIds;
            contextStrategyName = contextStrategy.name();
        } else {
            // VECTOR_RAG 保持旧逻辑：按问题向量检索若干 chunk 后构造 prompt。
            sources = ragRetrievalService.retrieveSources(retrievalQuestion, topK, effectivePaperIds);
            long promptStartedAt = RagTimingTrace.start();
            try {
                prompt = ragPromptService.buildPrompt(answerQuestion, sources);
            } finally {
                RagTimingTrace.addElapsed(RagTimingTrace.PROMPT_BUILD, promptStartedAt);
            }
            contextTokenCount = estimateSourceTokens(sources);
            contextPaperIds = effectivePaperIds;
            contextStrategyName = contextStrategy.name();
        }

        ConversationContextService.Prepared conversation = withConversation
                ? conversationContextService.prepare(historySnapshot, request.getQuestion(), prompt, effectivePaperIds) : null;

        if (streamListener != null) {
            streamListener.onMetadata(new RagStreamMetadata(
                    contextStrategyName,
                    retrievalQuestion,
                    contextTokenCount,
                    contextPaperIds,
                    sources,
                    llmService.provider(),
                    llmService.modelName(),
                    evidencePlan,
                    conversation == null ? null : conversation.info()
            ));
        }

        // 2. 通过统一的大模型服务生成回答。
        long llmStartedAt = RagTimingTrace.start();
        long[] firstDeltaAt = {-1L};
        String answer;
        try (var usageStage = ExperimentTrace.stage("answer")) {
            if (streamListener == null) {
                answer = conversation == null ? llmService.generateAnswer(prompt)
                        : llmService.generateMessages(conversation.messages());
            } else {
                java.util.function.Consumer<String> onDelta = delta -> {
                    if (delta == null || delta.isEmpty()) {
                        return;
                    }
                    if (firstDeltaAt[0] < 0L) {
                        firstDeltaAt[0] = System.nanoTime();
                    }
                    streamListener.onDelta(delta);
                };
                answer = conversation == null ? llmService.generateAnswerStream(prompt, onDelta)
                        : llmService.generateMessagesStream(conversation.messages(), onDelta);
            }
        } finally {
            RagTimingTrace.addElapsed(RagTimingTrace.LLM_GENERATION, llmStartedAt);
        }

        // 3. 保存对话历史。
        long historyStartedAt = RagTimingTrace.start();
        Long sessionId;
        try {
            // 仅实验入口不落库；普通聊天仍按原流程保存。
            sessionId = ExperimentTrace.active() ? null : chatHistoryService.saveRagChat(
                    request.getSessionId(),
                    resolveHistoryPaperId(contextPaperIds),
                    request.getQuestion(),
                    answer,
                    llmService.provider(),
                    llmService.modelName(),
                    sources
            );
        } finally {
            RagTimingTrace.addElapsed(RagTimingTrace.HISTORY_SAVE, historyStartedAt);
        }

        long postProcessingStartedAt = RagTimingTrace.start();
        boolean suggestSaveAsIdea;
        String ideaSuggestionReason;
        try (var usageStage = ExperimentTrace.stage("ideaSuggestion")) {
            suggestSaveAsIdea = ideaSuggestionService.shouldSuggestSaveAsIdea(
                    request.getQuestion(), answer, sources);
            ideaSuggestionReason = ideaSuggestionService.buildSuggestionReason(
                    request.getQuestion(), answer, sources);
        } finally {
            RagTimingTrace.addElapsed(RagTimingTrace.POST_PROCESSING, postProcessingStartedAt);
        }

        RagChatResponse response = new RagChatResponse();
        response.setSessionId(sessionId);
        response.setQuestion(request.getQuestion());
        response.setRetrievalQuestion(retrievalQuestion);
        response.setAnswer(answer);
        response.setSources(sources);
        response.setPrompt(prompt);
        response.setModelProvider(llmService.provider());
        response.setModelName(llmService.modelName());
        response.setSourceCount(sources == null ? 0 : sources.size());
        response.setSuggestSaveAsIdea(suggestSaveAsIdea);
        response.setIdeaSuggestionReason(ideaSuggestionReason);
        response.setContextStrategy(contextStrategyName);
        response.setContextTokenCount(contextTokenCount);
        response.setContextPaperIds(contextPaperIds);
        response.setEvidencePlan(evidencePlan);
        response.setConversationContext(conversation == null ? null : conversation.info());

        // LIBRARY_DISCOVERY 模式下计算论文级相关度
        if ((ContextStrategy.LIBRARY_DISCOVERY.equals(contextStrategy)
                || "STRUCTURED_FIRST_LIBRARY".equals(contextStrategyName))
                && sources != null && !sources.isEmpty()) {
            long relevanceStartedAt = RagTimingTrace.start();
            List<PaperRelevance> paperRelevance = paperDiscoveryService.aggregateByPaper(sources, 6);
            response.setPaperRelevance(paperRelevance);
            RagTimingTrace.addElapsed(RagTimingTrace.POST_PROCESSING, relevanceStartedAt);
        }

        RagTiming timing = RagTimingTrace.snapshot(totalStartedAt);
        if (firstDeltaAt[0] >= 0L) {
            timing.setFirstTokenMs(TimeUnit.NANOSECONDS.toMillis(firstDeltaAt[0] - llmStartedAt));
            timing.setFirstContentMs(TimeUnit.NANOSECONDS.toMillis(firstDeltaAt[0] - totalStartedAt));
        }
        response.setTiming(timing);
        return response;
        } finally {
            RagTimingTrace.clear();
        }
    }


    /**
     * 解析本轮 RAG 问答的有效文献范围。
     *
     * paperIds 用于新的多选参考论文场景，优先级高于旧的 paperId；
     * 两者都为空时返回空列表，表示检索全部论文。
     */
    private List<Long> resolvePaperIds(RagChatRequest request) {
        if (request.getPaperIds() != null && !request.getPaperIds().isEmpty()) {
            return request.getPaperIds()
                    .stream()
                    .filter(id -> id != null && id > 0)
                    .distinct()
                    .toList();
        }

        if (request.getPaperId() != null && request.getPaperId() > 0) {
            return List.of(request.getPaperId());
        }

        return List.of();
    }


    private List<RagSource> retrieveHybridRawSources(String question, int topK, List<Long> paperIds) {
        try {
            return ragRetrievalService.retrieveSources(question, topK, paperIds);
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    /**
     * 对话历史仍保留单个 paperId 字段。
     *
     * 只有本轮限定到一篇文献时才写入该字段；多篇或全库检索时写 null。
     */
    private Long resolveHistoryPaperId(List<Long> paperIds) {
        return paperIds != null && paperIds.size() == 1 ? paperIds.get(0) : null;
    }

    /**
     * 估算 VECTOR_RAG 放入上下文的 source token 数，便于和 FULL_TEXT_PARSED 做调试对比。
     */
    private Integer estimateSourceTokens(List<RagSource> sources) {
        if (sources == null || sources.isEmpty()) {
            return 0;
        }

        return sources.stream()
                .map(RagSource::getContent)
                .filter(content -> content != null && !content.isBlank())
                .mapToInt(content -> Math.max(1, (int) Math.ceil(content.length() / 4.0)))
                .sum();
    }


}
