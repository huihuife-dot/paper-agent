package com.myagent.assistant.observability;

import com.myagent.assistant.rag.dto.RagTiming;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 同步 RAG 请求的轻量线程内计时器。
 *
 * 只有 RagChatService 主动 begin 后才记录，下层 Qdrant / 检索服务可安全调用；
 * finally 中必须 clear，避免线程池复用导致数据串请求。
 */
public final class RagTimingTrace {

    public static final String STRATEGY_SELECTION = "strategySelection";
    public static final String QUERY_REWRITE = "queryRewrite";
    public static final String EMBEDDING = "embedding";
    public static final String VECTOR_SEARCH = "vectorSearch";
    public static final String SOURCE_HYDRATION = "sourceHydration";
    public static final String BM25 = "bm25";
    public static final String FUSION_RANKING = "fusionRanking";
    public static final String CONTEXT_BUILD = "contextBuild";
    public static final String PROMPT_BUILD = "promptBuild";
    public static final String LLM_GENERATION = "llmGeneration";
    public static final String HISTORY_SAVE = "historySave";
    public static final String POST_PROCESSING = "postProcessing";

    private static final ThreadLocal<Map<String, Long>> TIMINGS = new ThreadLocal<>();

    private RagTimingTrace() {
    }

    public static void begin() {
        TIMINGS.set(new LinkedHashMap<>());
    }

    public static long start() {
        return System.nanoTime();
    }

    public static void addElapsed(String stage, long startedAtNanos) {
        Map<String, Long> timings = TIMINGS.get();
        if (timings == null || stage == null) {
            return;
        }
        long elapsed = Math.max(0L, System.nanoTime() - startedAtNanos);
        timings.merge(stage, elapsed, Long::sum);
    }

    public static RagTiming snapshot(long totalStartedAtNanos) {
        Map<String, Long> timings = TIMINGS.get();
        Map<String, Long> safeTimings = timings == null ? Map.of() : timings;
        long totalNanos = Math.max(0L, System.nanoTime() - totalStartedAtNanos);

        RagTiming result = new RagTiming();
        result.setStrategySelectionMs(toMillis(safeTimings.get(STRATEGY_SELECTION)));
        result.setQueryRewriteMs(toMillis(safeTimings.get(QUERY_REWRITE)));
        result.setEmbeddingMs(toMillis(safeTimings.get(EMBEDDING)));
        result.setVectorSearchMs(toMillis(safeTimings.get(VECTOR_SEARCH)));
        result.setSourceHydrationMs(toMillis(safeTimings.get(SOURCE_HYDRATION)));
        result.setBm25Ms(toMillis(safeTimings.get(BM25)));
        result.setFusionRankingMs(toMillis(safeTimings.get(FUSION_RANKING)));
        result.setContextBuildMs(toMillis(safeTimings.get(CONTEXT_BUILD)));
        result.setPromptBuildMs(toMillis(safeTimings.get(PROMPT_BUILD)));
        result.setLlmGenerationMs(toMillis(safeTimings.get(LLM_GENERATION)));
        result.setHistorySaveMs(toMillis(safeTimings.get(HISTORY_SAVE)));
        result.setPostProcessingMs(toMillis(safeTimings.get(POST_PROCESSING)));

        long accountedNanos = safeTimings.values().stream().mapToLong(Long::longValue).sum();
        result.setOtherMs(TimeUnit.NANOSECONDS.toMillis(Math.max(0L, totalNanos - accountedNanos)));
        result.setTotalMs(TimeUnit.NANOSECONDS.toMillis(totalNanos));
        result.setDominantStage(safeTimings.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("other"));
        return result;
    }

    public static void clear() {
        TIMINGS.remove();
    }

    private static long toMillis(Long nanos) {
        return nanos == null ? 0L : TimeUnit.NANOSECONDS.toMillis(Math.max(0L, nanos));
    }
}

