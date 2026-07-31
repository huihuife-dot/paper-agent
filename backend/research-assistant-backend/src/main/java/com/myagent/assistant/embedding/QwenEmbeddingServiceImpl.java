package com.myagent.assistant.embedding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

/**
 * 通义千问 Qwen Embedding 实现。
 *
 * 使用 DashScope OpenAI-compatible embeddings 接口：
 * POST {baseUrl}/embeddings
 *
 * QdrantService 不直接依赖本类，只依赖 EmbeddingService。
 */
@Service
@ConditionalOnProperty(name = "embedding.provider", havingValue = "qwen")
public class QwenEmbeddingServiceImpl implements EmbeddingService {

    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final int dimension;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public QwenEmbeddingServiceImpl(
            @Value("${qwen.embedding.api.key}") String apiKey,
            @Value("${qwen.embedding.base-url}") String baseUrl,
            @Value("${qwen.embedding.model}") String model,
            @Value("${qwen.embedding.dimension}") int dimension,
            ObjectMapper objectMapper
    ) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.model = model;
        this.dimension = dimension;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newHttpClient();
    }

    @Override
    public List<Double> embed(String text) {
        if (text == null || text.isBlank()) {
            throw new RuntimeException("Embedding 文本不能为空");
        }

        if (apiKey == null || apiKey.isBlank()) {
            throw new RuntimeException("Qwen Embedding API Key 未配置，请先设置环境变量 DASHSCOPE_API_KEY");
        }

        try {
            // OpenAI-compatible embeddings 请求体。
            // input 是需要向量化的文本。
            String requestBody = objectMapper.writeValueAsString(Map.of(
                    "model", model,
                    "input", text
            ));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/embeddings"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new RuntimeException("Qwen Embedding API 调用失败，HTTP 状态码："
                        + response.statusCode()
                        + "，响应内容："
                        + response.body());
            }

            JsonNode embeddingNode = objectMapper.readTree(response.body())
                    .path("data")
                    .path(0)
                    .path("embedding");

            if (!embeddingNode.isArray() || embeddingNode.isEmpty()) {
                throw new RuntimeException("Qwen Embedding API 未返回有效向量，响应内容：" + response.body());
            }

            List<Double> vector = objectMapper.convertValue(
                    embeddingNode,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Double.class)
            );

            if (vector.size() != dimension) {
                throw new RuntimeException("Qwen Embedding 向量维度不匹配，配置维度："
                        + dimension
                        + "，实际返回："
                        + vector.size()
                        + "。请检查 qwen.embedding.dimension 配置。");
            }

            return vector;

        } catch (Exception e) {
            throw new RuntimeException("调用 Qwen Embedding API 失败：" + e.getMessage(), e);
        }
    }

    @Override
    public List<List<Double>> embedAll(List<String> texts) {
        if (texts == null || texts.isEmpty() || texts.stream().anyMatch(text -> text == null || text.isBlank())) {
            throw new RuntimeException("Embedding 文本不能为空");
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new RuntimeException("Qwen Embedding API Key 未配置，请先设置环境变量 DASHSCOPE_API_KEY");
        }
        try {
            String requestBody = objectMapper.writeValueAsString(Map.of("model", model, "input", texts));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/embeddings"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new RuntimeException("Qwen Embedding 批量 API 调用失败，HTTP 状态码："
                        + response.statusCode() + "，响应内容：" + response.body());
            }
            JsonNode data = objectMapper.readTree(response.body()).path("data");
            if (!data.isArray() || data.size() != texts.size()) {
                throw new RuntimeException("Qwen Embedding 批量返回数量不匹配");
            }
            List<List<Double>> vectors = new java.util.ArrayList<>();
            for (JsonNode item : data) {
                List<Double> vector = objectMapper.convertValue(item.path("embedding"),
                        objectMapper.getTypeFactory().constructCollectionType(List.class, Double.class));
                if (vector.size() != dimension) {
                    throw new RuntimeException("Qwen Embedding 向量维度不匹配，配置维度："
                            + dimension + "，实际返回：" + vector.size());
                }
                vectors.add(vector);
            }
            return vectors;
        } catch (Exception e) {
            throw new RuntimeException("调用 Qwen Embedding 批量 API 失败：" + e.getMessage(), e);
        }
    }

    @Override
    public int dimension() {
        return dimension;
    }

    @Override
    public String provider() {
        return "qwen";
    }

    @Override
    public String modelName() {
        return model;
    }
}
