package com.myagent.assistant.experiment;

import com.fasterxml.jackson.databind.JsonNode;
import com.myagent.assistant.rag.dto.EvidenceQueryPlan;
import java.util.ArrayList;
import java.util.List;

/** 只由受保护的同步实验端点打开；普通请求不分配记录，线程复用时必须关闭。 */
public final class ExperimentTrace implements AutoCloseable {
    public enum Variant { BASELINE, JEV }
    private static final ThreadLocal<ExperimentTrace> CURRENT = new ThreadLocal<>();
    public final Variant variant;
    public final List<Call> calls = new ArrayList<>();
    public final List<String> fallbacks = new ArrayList<>();
    public EvidenceQueryPlan plan;
    public JsonNode decisions;
    private String stage = "retrievalRewrite";

    private ExperimentTrace(Variant variant) { this.variant = variant; }
    public static ExperimentTrace open(Variant variant) {
        if (CURRENT.get() != null) throw new IllegalStateException("不允许嵌套实验");
        ExperimentTrace trace = new ExperimentTrace(variant);
        CURRENT.set(trace);
        return trace;
    }
    public static ExperimentTrace current() { return CURRENT.get(); }
    public static boolean active() { return current() != null; }
    public static Stage stage(String name) {
        ExperimentTrace trace = current();
        String previous = trace == null ? null : trace.stage;
        if (trace != null) trace.stage = name;
        return () -> { if (trace != null) trace.stage = previous; };
    }
    public interface Stage extends AutoCloseable { @Override void close(); }

    public static Call startCall(String provider, String model, String kind) {
        ExperimentTrace trace = current();
        if (trace == null) return null;
        Call call = new Call(provider, model, kind, "embedding".equals(kind) ? "embedding" : trace.stage);
        trace.calls.add(call);
        return call;
    }
    public static void received(Call call, JsonNode root) {
        if (call == null || root == null) return;
        if (root.path("model").isTextual()) call.actualModel = root.path("model").asText();
        JsonNode usage = root.path("usage");
        if (!usage.isObject()) return;
        // OpenRouter Decisions 的 usage.cost 是供应商返回的美元费用，优先保留而非反推。
        JsonNode cost = usage.path("cost");
        if ("openrouter".equals(call.provider) && cost.isNumber() && Double.isFinite(cost.asDouble()) && cost.asDouble() >= 0) {
            call.reportedCost = cost.asDouble(); call.costCurrency = "USD";
        }
        call.inputTokens = token(usage, "prompt_tokens", "input_tokens");
        call.outputTokens = token(usage, "completion_tokens", "output_tokens");
        // Embedding 没有生成输出；不是把缺失的文本生成用量当作 0。
        if ("embedding".equals(call.kind)) call.outputTokens = 0L;
        call.cachedInputTokens = token(usage.path("prompt_tokens_details"), "cached_tokens", "cached_tokens");
        if (call.cachedInputTokens == null) call.cachedInputTokens = token(usage, "prompt_cache_hit_tokens", "cache_read_input_tokens");
    }
    private static Long token(JsonNode node, String key, String alternative) {
        JsonNode value = node.has(key) ? node.path(key) : node.path(alternative);
        return value.isIntegralNumber() && value.canConvertToLong() && value.asLong() >= 0 ? value.asLong() : null;
    }
    public static void finish(Call call, boolean success) {
        if (call == null) return;
        call.success = success;
        call.elapsedMs = (System.nanoTime() - call.startedAt) / 1_000_000;
    }
    @Override public void close() { CURRENT.remove(); }

    /** 不保存 key、请求头或原始 HTTP 错误正文。null 用量意味着未知，不可按 0 计费。 */
    public static final class Call {
        public final String provider, requestedModel, kind, stage;
        public String actualModel;
        public Long inputTokens, outputTokens, cachedInputTokens;
        public Double reportedCost;
        public String costCurrency;
        public Integer httpStatus;
        public boolean success;
        public long elapsedMs;
        private final long startedAt = System.nanoTime();
        private Call(String provider, String model, String kind, String stage) {
            this.provider = provider.toLowerCase(java.util.Locale.ROOT);
            this.requestedModel = model; this.kind = kind; this.stage = stage;
        }
    }
}
