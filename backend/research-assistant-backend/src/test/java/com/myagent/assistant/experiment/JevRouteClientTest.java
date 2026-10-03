package com.myagent.assistant.experiment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.myagent.assistant.rag.dto.EvidenceQueryPlan;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class JevRouteClientTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private JevRouteClient client(String url) { return new JevRouteClient(mapper, "test-only", url, "jev-1.13.0", 1, 0.8); }
    @Test void legacyVariableAutomaticallySelectsOpenRouter() {
        var env = new org.springframework.mock.env.MockEnvironment()
                .withProperty("TYPESAFE_API_KEY", "  sk-or-test-only  ");
        for (String provider : List.of("auto", "", "openrouter")) {
            var client = new JevRouteClient(mapper, provider, env);
            assertTrue(client.configured());
            assertEquals("openrouter", client.provider());
            assertEquals("https://openrouter.ai/api/alpha/decisions", client.endpoint());
            assertEquals("typesafe/jev-1.13", client.model());
        }
    }
    @Test void officialKeyAndMissingKeyKeepTypeSafeDefaults() {
        var env = new org.springframework.mock.env.MockEnvironment();
        assertFalse(new JevRouteClient(mapper, "auto", env).configured());
        env.withProperty("TYPESAFE_API_KEY", "official-test-only");
        var client = new JevRouteClient(mapper, "auto", env);
        assertTrue(client.configured()); assertEquals("typesafe", client.provider());
        assertEquals("https://api.typesafe.ai/v1/systemone", client.endpoint());
        assertEquals("jev-1.13.0", client.model());
    }
    @Test void explicitProviderIsNotSilentlyOverridden() {
        var env = new org.springframework.mock.env.MockEnvironment()
                .withProperty("TYPESAFE_API_KEY", "sk-or-test-only");
        var client = new JevRouteClient(mapper, "typesafe", env);
        assertEquals("typesafe", client.provider());
        assertEquals("JEV_KEY_PROVIDER_MISMATCH", assertThrows(IllegalStateException.class,
                () -> client.route("test", new EvidenceQueryPlan())).getMessage());
        assertThrows(IllegalArgumentException.class, () -> new JevRouteClient(mapper, "invalid", env));
    }
    @Test void genericKeyTakesPriorityAndBlankValuesAllowLegacyFallback() {
        var env = new org.springframework.mock.env.MockEnvironment()
                .withProperty("jev.api-key", "official-test-only")
                .withProperty("OPENROUTER_API_KEY", "sk-or-test-only")
                .withProperty("TYPESAFE_API_KEY", "sk-or-legacy-test-only");
        assertEquals("typesafe", new JevRouteClient(mapper, "auto", env).provider());
        env.withProperty("jev.api-key", " ").withProperty("OPENROUTER_API_KEY", " ");
        var client = new JevRouteClient(mapper, "auto", env);
        assertEquals("openrouter", client.provider()); assertTrue(client.configured());
    }
    @Test void legacyKeyIsActuallyUsedInAuthorizationHeader() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        try {
            var env = new org.springframework.mock.env.MockEnvironment()
                    .withProperty("TYPESAFE_API_KEY", "sk-or-legacy-test-only")
                    .withProperty("jev.endpoint", "http://127.0.0.1:" + server.getAddress().getPort());
            var client = new JevRouteClient(mapper, "auto", env);
            server.createContext("/", exchange -> {
                if (!"Bearer sk-or-legacy-test-only".equals(exchange.getRequestHeaders().getFirst("Authorization"))) {
                    exchange.sendResponseHeaders(401, -1); exchange.close(); return;
                }
                byte[] body = mapper.writeValueAsBytes(valid(client));
                exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body); exchange.close();
            }); server.start();
            var rule = new EvidenceQueryPlan(); rule.setScope("SINGLE"); rule.setIntent("METHOD");
            assertEquals(List.of("METHOD", "RESULT"), client.route("为什么有效？", rule).getKnowledgeTypes());
        } finally { server.stop(0); }
    }
    @Test void openRouterUsesDecisionsEndpointAndItsOwnKey() {
        var env = new org.springframework.mock.env.MockEnvironment()
                .withProperty("OPENROUTER_API_KEY", "sk-or-test-only")
                .withProperty("TYPESAFE_API_KEY", "not-used");
        var client = new JevRouteClient(mapper, "openrouter", env);
        assertEquals("https://openrouter.ai/api/alpha/decisions", client.endpoint());
        assertEquals("typesafe/jev-1.13", client.model());
        assertEquals("openrouter", client.provider()); assertTrue(client.configured());
        assertFalse(new JevRouteClient(mapper, "openrouter", new org.springframework.mock.env.MockEnvironment()
                .withProperty("TYPESAFE_API_KEY", "wrong-place")).configured());
    }
    @Test void refusesSendingOpenRouterKeyToTypeSafe() {
        var client = new JevRouteClient(mapper, "sk-or-test-only", "https://api.typesafe.ai/v1/systemone", "jev-1.13.0", 1, 0.8);
        assertEquals("JEV_KEY_PROVIDER_MISMATCH", assertThrows(IllegalStateException.class,
                () -> client.route("test", new EvidenceQueryPlan())).getMessage());
    }
    @Test void openRouterResponsePreservesActualSnapshotAndReportedCost() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        try {
            var client = new JevRouteClient(mapper, "openrouter", "sk-or-test-only",
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/api/alpha/decisions", "typesafe/jev-1.13", 1, 0.8);
            server.createContext("/api/alpha/decisions", exchange -> {
                var request = mapper.readTree(exchange.getRequestBody());
                assertEquals("typesafe/jev-1.13", request.path("model").asText());
                assertFalse(request.has("messages"));
                var root = valid(client); root.put("model", "typesafe/jev-1.13-20260917");
                ((ObjectNode) root.path("usage")).put("cost", 0.000014994);
                byte[] body = mapper.writeValueAsBytes(root);
                exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body); exchange.close();
            }); server.start();
            try (var trace = ExperimentTrace.open(ExperimentTrace.Variant.JEV)) {
                var rule = new EvidenceQueryPlan(); rule.setScope("SINGLE"); rule.setIntent("METHOD");
                client.route("方法为何有效？", rule);
                var call = trace.calls.getFirst();
                assertEquals("openrouter", call.provider); assertEquals("USD", call.costCurrency);
                assertEquals(0.000014994, call.reportedCost); assertEquals("typesafe/jev-1.13-20260917", call.actualModel);
            }
        } finally { server.stop(0); }
    }
    ObjectNode valid(JevRouteClient client) {
        ObjectNode root = mapper.createObjectNode(); root.put("model", "jev-1.13.0");
        ObjectNode answers = root.putObject("answers");
        for (String id : client.questions().keySet()) {
            boolean needed = List.of("knowledge_METHOD", "knowledge_RESULT", "section_METHOD").contains(id);
            ObjectNode a = answers.putObject(id); a.put("type", "choice"); a.put("confidence", 0.91);
            a.put("choice", needed ? "NEEDED" : "NOT_NEEDED");
            a.putObject("probabilities").put("NEEDED", needed ? 0.95 : 0.02)
                    .put("NOT_NEEDED", needed ? 0.02 : 0.95).put("UNCERTAIN", 0.03);
        }
        root.putObject("usage").put("input_tokens", 123).put("output_tokens", 20);
        return root;
    }
    @Test void parsesMultipleLabelsAndRejectsUncertainty() {
        var client = client("http://localhost"); var root = valid(client);
        var route = client.parse(root, "METHOD");
        assertEquals(List.of("METHOD", "RESULT"), route.getKnowledgeTypes());
        assertEquals(List.of("METHOD"), route.getSectionTypes());
        ((ObjectNode) root.path("answers").path("knowledge_METHOD")).put("confidence", 0.2);
        assertThrows(IllegalStateException.class, () -> client.parse(root, "METHOD"));
    }
    @Test void rejectsMissingIllegalAndInconsistentAnswers() {
        var client = client("http://localhost");
        var missing = valid(client); ((ObjectNode) missing.path("answers")).remove("knowledge_METHOD");
        assertThrows(IllegalStateException.class, () -> client.parse(missing, "METHOD"));
        var illegal = valid(client); ((ObjectNode) illegal.path("answers").path("knowledge_METHOD")).put("choice", "RUN_SQL");
        assertThrows(IllegalStateException.class, () -> client.parse(illegal, "METHOD"));
        var wrong = valid(client); ((ObjectNode) wrong.path("answers").path("knowledge_METHOD").path("probabilities")).put("NEEDED", 0.01);
        assertThrows(IllegalStateException.class, () -> client.parse(wrong, "METHOD"));
    }
    @Test void rejectsEmptyAndExplicitUncertainRoutes() {
        var client = client("http://localhost"); var root = valid(client);
        root.path("answers").forEach(node -> {
            ((ObjectNode) node).put("choice", "NOT_NEEDED");
            ((ObjectNode) node.path("probabilities")).put("NEEDED", 0.02).put("NOT_NEEDED", 0.95);
        });
        assertThrows(IllegalStateException.class, () -> client.parse(root, "METHOD"));
        var a = (ObjectNode) root.path("answers").path("knowledge_METHOD");
        a.put("choice", "UNCERTAIN");
        ((ObjectNode) a.path("probabilities")).put("UNCERTAIN", 0.95).put("NOT_NEEDED", 0.03);
        assertThrows(IllegalStateException.class, () -> client.parse(root, "METHOD"));
    }
    @Test void recordsRealHttpUsageWithoutLeakingKey() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        try {
            var client = client("http://127.0.0.1:" + server.getAddress().getPort());
            server.createContext("/", exchange -> {
                JsonNodeAssertions.checkRequest(mapper.readTree(exchange.getRequestBody()));
                byte[] body = mapper.writeValueAsBytes(valid(client));
                exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body); exchange.close();
            }); server.start();
            try (var trace = ExperimentTrace.open(ExperimentTrace.Variant.JEV)) {
                EvidenceQueryPlan rule = new EvidenceQueryPlan(); rule.setScope("SINGLE"); rule.setIntent("METHOD");
                client.route("为什么这个方法有效？", rule);
                assertEquals(123L, trace.calls.getFirst().inputTokens);
                assertTrue(trace.calls.getFirst().success);
                assertFalse(mapper.writeValueAsString(trace.calls).contains("test-only"));
            }
        } finally { server.stop(0); }
    }
    @Test void httpFailureIsRecordedAndSanitized() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        try {
            server.createContext("/", exchange -> {
                byte[] body = "secret-response".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(429, body.length); exchange.getResponseBody().write(body); exchange.close();
            }); server.start();
            var client = client("http://127.0.0.1:" + server.getAddress().getPort());
            try (var trace = ExperimentTrace.open(ExperimentTrace.Variant.JEV)) {
                EvidenceQueryPlan rule = new EvidenceQueryPlan(); rule.setScope("SINGLE");
                var e = assertThrows(IllegalStateException.class, () -> client.route("方法", rule));
                assertEquals("JEV_HTTP_429", e.getMessage());
                assertFalse(trace.calls.getFirst().success); assertNull(trace.calls.getFirst().inputTokens);
            }
        } finally { server.stop(0); }
    }
    @Test void timeoutIsBoundedAndUnknownUsageIsNotZero() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        try {
            server.createContext("/", exchange -> {
                try { Thread.sleep(1500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                exchange.close();
            }); server.start();
            var client = client("http://127.0.0.1:" + server.getAddress().getPort());
            try (var trace = ExperimentTrace.open(ExperimentTrace.Variant.JEV)) {
                var rule = new EvidenceQueryPlan(); rule.setScope("SINGLE");
                var error = assertThrows(IllegalStateException.class, () -> client.route("方法为什么有效？", rule));
                assertTrue(error.getMessage().contains("Timeout"));
                assertFalse(trace.calls.getFirst().success); assertNull(trace.calls.getFirst().inputTokens);
            }
        } finally { server.stop(0); }
    }
    private static class JsonNodeAssertions {
        static void checkRequest(com.fasterxml.jackson.databind.JsonNode root) {
            assertEquals("jev-1.13.0", root.path("model").asText());
            assertEquals(26, root.path("questions").size());
            assertFalse(root.path("state").has("paperIds"));
        }
    }
}
