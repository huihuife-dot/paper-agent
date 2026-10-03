package com.myagent.assistant.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenAiCompatibleLlmClientTest {

    @Test
    @Timeout(15)
    void serializesRealRolesForNormalAndSseAndKeepsSinglePromptCompatible() throws Exception {
        var mapper = new ObjectMapper();
        List<JsonNode> requests = new CopyOnWriteArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            JsonNode body = mapper.readTree(exchange.getRequestBody());
            requests.add(body);
            boolean streaming = body.path("stream").asBoolean();
            String response = streaming
                    ? "data: {\"choices\":[{\"delta\":{\"content\":\"连续\"}}]}\n\ndata: {\"choices\":[{\"delta\":{\"content\":\"回答\"}}]}\n\ndata: [DONE]\n\n"
                    : "{\"choices\":[{\"message\":{\"content\":\"连续回答\"}}]}";
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", streaming ? "text/event-stream; charset=utf-8" : "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (var output = exchange.getResponseBody()) { output.write(bytes); }
            exchange.close();
        });
        server.start();
        try {
            var client = new OpenAiCompatibleLlmClient("test", "placeholder", "http://127.0.0.1:" + server.getAddress().getPort(),
                    "test-model", 256, 0.1, mapper);
            var messages = List.of(new LlmMessage("system", "规则"), new LlmMessage("user", "旧问题"),
                    new LlmMessage("assistant", "| A | B |\n| 1 | 2 |"), new LlmMessage("user", "改成中文"));
            assertThat(client.generateMessages(messages)).isEqualTo("连续回答");
            List<String> deltas = new ArrayList<>();
            assertThat(client.generateMessagesStream(messages, deltas::add)).isEqualTo("连续回答");
            assertThat(deltas).containsExactly("连续", "回答");
            assertThat(client.generateAnswer("原字符串入口")).isEqualTo("连续回答");
            assertThat(client.generateAnswerStream("旧流式入口", null)).isEqualTo("连续回答");
            assertThat(requests).hasSize(4);
            assertThat(requests.get(0).path("messages")).isEqualTo(mapper.valueToTree(messages));
            assertThat(requests.get(1).path("messages")).isEqualTo(requests.get(0).path("messages"));
            assertThat(requests.get(1).path("stream").asBoolean()).isTrue();
            assertThat(requests.get(0).path("max_tokens").asInt()).isEqualTo(256);
            assertThat(requests.get(2).path("messages")).isEqualTo(mapper.valueToTree(List.of(new LlmMessage("user", "原字符串入口"))));
            assertThat(requests.get(3).path("messages")).isEqualTo(mapper.valueToTree(List.of(new LlmMessage("user", "旧流式入口"))));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void rejectsInvalidRolesEmptyMessagesAndMissingCurrentUserBeforeNetwork() {
        assertThatThrownBy(() -> new LlmMessage(null, "test")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LlmMessage("tool", "test")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LlmMessage("user", " ")).isInstanceOf(IllegalArgumentException.class);
        var client = new OpenAiCompatibleLlmClient("test", "key", "http://localhost", "model", 100, 0.1, new ObjectMapper());
        assertThatThrownBy(() -> client.generateMessages(List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> client.generateMessages(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> client.generateMessages(List.of(new LlmMessage("assistant", "旧回答"))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("最后一条");
    }

    @Test
    void parseStreamLineCollectsDeltaContentAndIgnoresControlLines() {
        OpenAiCompatibleLlmClient client = new OpenAiCompatibleLlmClient(
                "test", "key", "http://localhost", "model", 100, 0.1, new ObjectMapper());
        StringBuilder answer = new StringBuilder();
        List<String> deltas = new ArrayList<>();

        client.parseStreamLine("", answer, deltas::add);
        client.parseStreamLine("data: {\"choices\":[{\"delta\":{\"content\":\"你好\"}}]}", answer, deltas::add);
        client.parseStreamLine("data: {\"choices\":[{\"delta\":{\"content\":\"，世界\"}}]}", answer, deltas::add);
        client.parseStreamLine("data: [DONE]", answer, deltas::add);

        assertThat(deltas).containsExactly("你好", "，世界");
        assertThat(answer.toString()).isEqualTo("你好，世界");
    }
}
