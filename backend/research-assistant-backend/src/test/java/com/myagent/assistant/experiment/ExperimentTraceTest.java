package com.myagent.assistant.experiment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myagent.assistant.llm.OpenAiCompatibleLlmClient;
import com.myagent.assistant.embedding.QwenEmbeddingServiceImpl;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class ExperimentTraceTest {
    ObjectMapper mapper = new ObjectMapper();
    @Test void noTraceMeansNoRecording() { assertNull(ExperimentTrace.startCall("qwen", "model", "generation")); }
    @Test void unknownTokensStayNullAndNestedStagesRestore() throws Exception {
        try (var trace = ExperimentTrace.open(ExperimentTrace.Variant.BASELINE)) {
            try (var ignored = ExperimentTrace.stage("routing")) {
                var call = ExperimentTrace.startCall("qwen", "model", "generation");
                ExperimentTrace.received(call, mapper.readTree("{\"usage\":{\"prompt_tokens\":5}}"));
                assertEquals("routing", call.stage); assertNull(call.outputTokens); assertNull(call.cachedInputTokens);
            }
            assertEquals("retrievalRewrite", ExperimentTrace.startCall("qwen", "model", "generation").stage);
        }
    }
    @Test void traceDoesNotCrossThreads() throws Exception {
        try (var trace = ExperimentTrace.open(ExperimentTrace.Variant.BASELINE)) {
            var future = java.util.concurrent.CompletableFuture.supplyAsync(ExperimentTrace::active);
            assertFalse(future.get());
        }
    }
    @Test void llmAndEmbeddingRecordProviderUsageWithoutChangingResult() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", e -> {
            byte[] b = "{\"model\":\"actual-v1\",\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":4,\"prompt_tokens_details\":{\"cached_tokens\":2}},\"choices\":[{\"message\":{\"content\":\"answer\"}}]}".getBytes(StandardCharsets.UTF_8);
            e.sendResponseHeaders(200, b.length); e.getResponseBody().write(b); e.close();
        });
        server.createContext("/embeddings", e -> {
            byte[] b = "{\"usage\":{\"prompt_tokens\":6},\"data\":[{\"embedding\":[0.1,0.2]}]}".getBytes(StandardCharsets.UTF_8);
            e.sendResponseHeaders(200, b.length); e.getResponseBody().write(b); e.close();
        });
        server.start();
        try (var trace = ExperimentTrace.open(ExperimentTrace.Variant.BASELINE)) {
            String url = "http://127.0.0.1:" + server.getAddress().getPort();
            var llm = new OpenAiCompatibleLlmClient("qwen", "test-key", url, "model", 20, 0.2, mapper);
            assertEquals("answer", llm.generateAnswer("question"));
            var embedding = new QwenEmbeddingServiceImpl("test-key", url, "embedding", 2, mapper);
            assertEquals(2, embedding.embed("question").size());
            assertEquals(1, embedding.embedAll(java.util.List.of("question")).size());
            assertEquals(3, trace.calls.size());
            var c = trace.calls.getFirst(); assertEquals(10L, c.inputTokens); assertEquals(4L, c.outputTokens);
            assertEquals(2L, c.cachedInputTokens); assertEquals("actual-v1", c.actualModel);
            assertEquals(0L, trace.calls.get(1).outputTokens); assertEquals("embedding", trace.calls.get(1).stage);
        } finally { server.stop(0); }
    }
}
