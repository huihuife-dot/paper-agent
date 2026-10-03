package com.myagent.assistant.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.function.Consumer;

/**
 * 通义千问 Qwen 大模型调用实现。
 *
 * 使用 DashScope 的 OpenAI-compatible mode：
 * https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions
 */
@Service
@ConditionalOnProperty(name = "llm.provider", havingValue = "qwen")
public class QwenLlmServiceImpl implements LlmService {

    private final String model;
    private final OpenAiCompatibleLlmClient client;

    public QwenLlmServiceImpl(
            @Value("${qwen.api.key}") String apiKey,
            @Value("${qwen.base-url}") String baseUrl,
            @Value("${qwen.model}") String model,
            @Value("${llm.max-tokens}") Integer maxTokens,
            @Value("${llm.temperature}") Double temperature,
            ObjectMapper objectMapper
    ) {
        this.model = model;
        this.client = new OpenAiCompatibleLlmClient(
                "Qwen",
                apiKey,
                baseUrl,
                model,
                maxTokens,
                temperature,
                objectMapper
        );
    }

    @Override
    public String generateAnswer(String prompt) {
        return client.generateAnswer(prompt);
    }

    @Override
    public String generateAnswerStream(String prompt, Consumer<String> onDelta) {
        return client.generateAnswerStream(prompt, onDelta);
    }

    @Override
    public String provider() {
        return "qwen";
    }

    @Override
    public String generateMessages(java.util.List<LlmMessage> messages) {
        return client.generateMessages(messages);
    }

    @Override
    public String generateMessagesStream(java.util.List<LlmMessage> messages, Consumer<String> onDelta) {
        return client.generateMessagesStream(messages, onDelta);
    }

    @Override
    public String modelName() {
        return model;
    }
}
