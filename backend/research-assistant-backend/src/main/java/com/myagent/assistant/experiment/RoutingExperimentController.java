package com.myagent.assistant.experiment;

import com.myagent.assistant.embedding.EmbeddingService;
import com.myagent.assistant.llm.LlmService;
import com.myagent.assistant.rag.dto.*;
import com.myagent.assistant.rag.service.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** 默认不存在该端点；服务端开关 + 单独访问令牌，不能通过普通聊天请求启用。 */
@RestController
@RequestMapping("/api/experiments/routing")
@ConditionalOnProperty(name = "rag.experiment.enabled", havingValue = "true")
public class RoutingExperimentController {
    private final String token;
    private final EvidenceQueryPlanner planner;
    private final RagChatService chat;
    private final LlmService llm;
    private final EmbeddingService embedding;
    private final JevRouteClient jev;
    private final Environment environment;
    public RoutingExperimentController(@Value("${rag.experiment.token:}") String token,
            EvidenceQueryPlanner planner, RagChatService chat, LlmService llm, EmbeddingService embedding,
            JevRouteClient jev, Environment environment) {
        this.token = token; this.planner = planner; this.chat = chat; this.llm = llm;
        this.embedding = embedding; this.jev = jev; this.environment = environment;
    }
    private void authorize(String supplied) {
        if (token == null || token.length() < 24) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "实验访问令牌未配置或少于24字符");
        if (supplied == null || !MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8)))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "实验令牌错误");
    }
    @GetMapping("/status")
    public Map<String, Object> status(@RequestHeader(value = "X-Experiment-Token", required = false) String supplied) {
        authorize(supplied);
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("protocolVersion", "routing-ab-v1");
        config.put("runtimeClassHash", runtimeClassHash());
        config.put("defaultVariant", "BASELINE");
        config.put("jevConfigured", jev.configured());
        config.put("jevProvider", jev.provider()); config.put("jevEndpoint", jev.endpoint());
        config.put("jevModel", jev.model()); config.put("jevMinConfidence", jev.minimumConfidence());
        config.put("llmProvider", llm.provider()); config.put("llmModel", llm.modelName());
        config.put("embeddingProvider", embedding.provider()); config.put("embeddingModel", embedding.modelName());
        // 只允许明确列出的非敏感配置进入报告。
        for (String key : List.of("llm.temperature", "llm.max-tokens", "rag.model-router-enabled",
                "rag.model-router-min-confidence", "rag.structured-first-enabled", "rag.full-text-token-budget"))
            config.put(key, environment.getProperty(key, "default"));
        return config;
    }

    private String runtimeClassHash() {
        try {
            MessageDigest hash = MessageDigest.getInstance("SHA-256");
            for (Class<?> type : List.of(JevRouteClient.class, ExperimentalEvidenceQueryPlanner.class, ExperimentTrace.class,
                    com.myagent.assistant.rag.service.impl.EvidenceQueryPlannerImpl.class,
                    com.myagent.assistant.rag.service.impl.RagChatServiceImpl.class,
                    com.myagent.assistant.llm.OpenAiCompatibleLlmClient.class)) {
                try (var stream = type.getResourceAsStream(type.getSimpleName() + ".class")) {
                    if (stream == null) return "unavailable";
                    hash.update(stream.readAllBytes());
                }
            }
            return HexFormat.of().formatHex(hash.digest());
        } catch (Exception e) { return "unavailable"; }
    }

    public record Request(String variant, String mode, RagChatRequest request) {}
    public record Response(boolean success, String error, String variant, String mode, Object result,
            EvidenceQueryPlan plan, List<String> fallbacks, Object decisions,
            List<ExperimentTrace.Call> calls, long elapsedMs) {}

    @PostMapping
    public Response run(@RequestHeader(value = "X-Experiment-Token", required = false) String supplied,
                        @RequestBody Request request) {
        authorize(supplied);
        if (request == null || request.request() == null) throw badRequest("缺少 request");
        ExperimentTrace.Variant variant;
        try { variant = ExperimentTrace.Variant.valueOf(request.variant()); }
        catch (Exception e) { throw badRequest("variant 必须是 BASELINE 或 JEV"); }
        if (!Set.of("route", "chat").contains(Objects.toString(request.mode(), ""))) throw badRequest("mode 必须是 route 或 chat");
        RagChatRequest input = request.request();
        if (input.getQuestion() == null || input.getQuestion().isBlank() || input.getQuestion().length() > 4000)
            throw badRequest("question 需为1至4000字符");
        if (input.getSessionId() != null) throw badRequest("第一阶段实验不读取/写入日常会话；追问请传入固定补全后的问题");
        if (input.getTopK() != null && (input.getTopK() < 1 || input.getTopK() > 30)) throw badRequest("topK 范围1至30");
        if (input.getPaperIds() != null && input.getPaperIds().size() > 20) throw badRequest("最多20篇论文");
        List<Long> ids = input.getPaperIds() != null && !input.getPaperIds().isEmpty() ? input.getPaperIds()
                : input.getPaperId() != null && input.getPaperId() > 0 ? List.of(input.getPaperId()) : List.of();
        long start = System.nanoTime();
        try (ExperimentTrace trace = ExperimentTrace.open(variant)) {
            Object result = null; String error = null;
            try {
                result = "route".equals(request.mode()) ? planner.plan(input.getQuestion(), ids) : chat.chat(input);
            } catch (RuntimeException e) {
                error = e.getClass().getSimpleName(); // 远程异常文本可能包含请求正文，不暴露。
            }
            return new Response(error == null, error, variant.name(), request.mode(), result, trace.plan,
                    List.copyOf(trace.fallbacks), trace.decisions, List.copyOf(trace.calls), (System.nanoTime() - start) / 1_000_000);
        }
    }
    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
