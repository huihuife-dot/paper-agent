package com.myagent.assistant.vision.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myagent.assistant.vision.service.VisionModelService;
import com.myagent.assistant.vision.service.VisionResultValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Service
public class QwenVisionModelService implements VisionModelService {
    private final ObjectMapper objectMapper;
    private final VisionResultValidator resultValidator;
    @Value("${qwen.vision.api.key:}") private String apiKey;
    @Value("${qwen.vision.base-url}") private String baseUrl;
    @Value("${qwen.vision.model:qwen-vl-max}") private String model;
    @Value("${vision.max-image-bytes:8388608}") private long maxImageBytes;
    @Value("${vision.request-timeout-seconds:60}") private int timeoutSeconds;
    public QwenVisionModelService(ObjectMapper objectMapper, VisionResultValidator resultValidator) {
        this.objectMapper = objectMapper;
        this.resultValidator = resultValidator;
    }

    @Override public String analyze(String assetType, String caption, String nearbyText, Path image) {
        if (apiKey == null || apiKey.isBlank()) throw new RuntimeException("未配置 DASHSCOPE_API_KEY，无法执行视觉分析");
        try {
            byte[] bytes = Files.readAllBytes(image);
            if (bytes.length > maxImageBytes) throw new RuntimeException("资产图片超过视觉分析大小限制");
            String prompt = """
                    你是论文复现证据抽取器。分析目标 %s，只返回一个 JSON 对象，不要 Markdown。
                    必须包含：
                    - purpose: 字符串，目标资产用途；
                    - components: 字符串数组，图中模块、表中方法或公式变量；
                    - connections: 字符串数组，模块连接、数据流或步骤关系；
                    - implementationFacts: 字符串数组，逐条抄录能够用于复现的明确事实；
                    - uncertainClaims: 字符串数组，无法确认、模糊或推断内容。

                    implementationFacts 必须尽量完整地逐项提取可见的模型名称、数据集/站点数量、输入特征、
                    历史窗口、预测步数与时间范围、预处理、超参数及其精确数值、指标、结果数值、算法步骤。
                    表格要逐行读取关键单元格，图和流程图要保留节点名称及顺序，公式要保留原式和变量定义。
                    每条使用“key = value”形式；看不清的值放入 uncertainClaims，严禁猜测。

                    图注：%s
                    附近正文：%s
                    """.formatted(assetType, caption == null ? "无" : caption,
                    nearbyText == null ? "无" : nearbyText);
            Map<String, Object> body = Map.of("model", model, "temperature", 0,
                    "messages", List.of(Map.of("role", "user", "content", List.of(
                            Map.of("type", "text", "text", prompt),
                            Map.of("type", "image_url", "image_url", Map.of("url", "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes)))))));
            SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
            requestFactory.setConnectTimeout(timeoutSeconds * 1000);
            requestFactory.setReadTimeout(timeoutSeconds * 1000);
            RestClient client = RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
            String result = null;
            RuntimeException lastFailure = null;
            for (int attempt = 1; attempt <= 2; attempt++) {
                try {
                    result = client.post().uri("/chat/completions").contentType(MediaType.APPLICATION_JSON)
                            .header("Authorization", "Bearer " + apiKey).body(body).retrieve().body(String.class);
                    break;
                } catch (RuntimeException e) {
                    lastFailure = e;
                }
            }
            if (result == null) throw new RuntimeException("Qwen-VL 两次调用均失败", lastFailure);
            JsonNode root = objectMapper.readTree(result); JsonNode content = root.at("/choices/0/message/content");
            if (content.isMissingNode() || content.asText().isBlank()) throw new RuntimeException("视觉模型未返回可用内容");
            return resultValidator.validateAndNormalize(content.asText());
        } catch (RuntimeException e) { throw e; } catch (Exception e) { throw new RuntimeException("Qwen-VL 调用失败", e); }
    }

    @Override public String analysisVersion() {
        return model + ":vision-schema-v2";
    }
}
