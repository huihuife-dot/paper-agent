package com.myagent.assistant.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myagent.assistant.experiment.ExperimentTrace;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * OpenAI-compatible Chat Completions 通用客户端。
 *
 * DeepSeek、Qwen compatible mode、智谱 GLM v4 都可以复用类似接口：
 * POST {baseUrl}/chat/completions
 *
 * 这样可以避免每接入一个模型供应商就复制一份 HTTP 调用代码。
 */
public class OpenAiCompatibleLlmClient {

    private final String providerName;
    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final Integer maxTokens;
    private final Double temperature;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public OpenAiCompatibleLlmClient(
            String providerName,
            String apiKey,
            String baseUrl,
            String model,
            Integer maxTokens,
            Double temperature,
            ObjectMapper objectMapper
    ) {
        this.providerName = providerName;
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.model = model;
        this.maxTokens = maxTokens;
        this.temperature = temperature;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newHttpClient();
    }

    /**
     * 调用兼容 OpenAI Chat Completions 格式的大模型接口。
     *
     * @param prompt RAG 服务构造好的完整 prompt
     * @return 模型生成的回答文本
     */
    public String generateAnswer(String prompt) {
        validateRequest(prompt);
        return generateMessages(List.of(new LlmMessage("user", prompt)));
    }

    public String generateMessages(List<LlmMessage> messages) {
        messages = validateMessages(messages);

        ExperimentTrace.Call usageCall = ExperimentTrace.startCall(providerName, model, "generation");
        boolean usageSuccess = false;
        try {
            // 构造 OpenAI Chat Completions 风格请求体。
            String requestBody = objectMapper.writeValueAsString(Map.of(
                    "model", model,
                    "messages", messages,
                    "temperature", temperature,
                    "max_tokens", maxTokens
            ));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/chat/completions"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            if (ExperimentTrace.active()) request = HttpRequest.newBuilder(request, (name, value) -> true)
                    .timeout(java.time.Duration.ofSeconds(60)).build();

            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

            if (usageCall != null) usageCall.httpStatus = response.statusCode();
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new RuntimeException(providerName + " API 调用失败，HTTP 状态码："
                        + response.statusCode()
                        + "，响应内容："
                        + response.body());
            }

            JsonNode root = objectMapper.readTree(response.body());
            ExperimentTrace.received(usageCall, root);
            JsonNode contentNode = root
                    .path("choices")
                    .path(0)
                    .path("message")
                    .path("content");

            if (contentNode.isMissingNode() || contentNode.asText().isBlank()) {
                throw new RuntimeException(providerName + " API 未返回有效回答，响应内容：" + response.body());
            }

            usageSuccess = true;
            return contentNode.asText();

        } catch (Exception e) {
            throw new RuntimeException("调用 " + providerName + " API 失败：" + e.getMessage(), e);
        } finally {
            ExperimentTrace.finish(usageCall, usageSuccess);
        }
    }

    /**
     * 使用 OpenAI-compatible SSE 协议增量读取回答。
     */
    public String generateAnswerStream(String prompt, Consumer<String> onDelta) {
        validateRequest(prompt);
        return generateMessagesStream(List.of(new LlmMessage("user", prompt)), onDelta);
    }

    public String generateMessagesStream(List<LlmMessage> messages, Consumer<String> onDelta) {
        messages = validateMessages(messages);

        try {
            String requestBody = objectMapper.writeValueAsString(Map.of(
                    "model", model,
                    "messages", messages,
                    "temperature", temperature,
                    "max_tokens", maxTokens,
                    "stream", true
            ));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/chat/completions"))
                    .header("Content-Type", "application/json")
                    .header("Accept", "text/event-stream")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<Stream<String>> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofLines());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                try (Stream<String> lines = response.body()) {
                    String body = String.join("\n", lines.toList());
                    throw new RuntimeException(providerName + " API 调用失败，HTTP 状态码："
                            + response.statusCode() + "，响应内容：" + body);
                }
            }

            StringBuilder answer = new StringBuilder();
            try (Stream<String> lines = response.body()) {
                lines.forEach(line -> parseStreamLine(line, answer, onDelta));
            }
            if (answer.isEmpty()) {
                throw new RuntimeException(providerName + " API 流式响应未返回有效回答");
            }
            return answer.toString();
        } catch (Exception e) {
            throw new RuntimeException("调用 " + providerName + " API 流式生成失败：" + e.getMessage(), e);
        }
    }

    void parseStreamLine(String line, StringBuilder answer, Consumer<String> onDelta) {
        if (line == null || line.isBlank() || !line.startsWith("data:")) {
            return;
        }
        String data = line.substring("data:".length()).trim();
        if (data.isEmpty() || "[DONE]".equals(data)) {
            return;
        }

        try {
            JsonNode root = objectMapper.readTree(data);
            JsonNode error = root.path("error");
            if (!error.isMissingNode()) {
                throw new RuntimeException(error.path("message").asText(error.toString()));
            }
            JsonNode content = root.path("choices").path(0).path("delta").path("content");
            if (!content.isMissingNode() && !content.isNull() && !content.asText().isEmpty()) {
                String delta = content.asText();
                answer.append(delta);
                if (onDelta != null) {
                    onDelta.accept(delta);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("解析 " + providerName + " 流式响应失败：" + data, e);
        }
    }

    private void validateRequest(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            throw new RuntimeException("Prompt 不能为空");
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new RuntimeException(providerName + " API Key 未配置");
        }
    }

    private List<LlmMessage> validateMessages(List<LlmMessage> messages) {
        if (messages == null || messages.isEmpty() || messages.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("消息列表不能为空或包含空消息");
        }
        if (apiKey == null || apiKey.isBlank()) throw new IllegalStateException(providerName + " API Key 未配置");
        if (!"user".equals(messages.get(messages.size() - 1).role())) {
            throw new IllegalArgumentException("对话最后一条必须为当前用户消息");
        }
        return List.copyOf(messages);
    }
}
