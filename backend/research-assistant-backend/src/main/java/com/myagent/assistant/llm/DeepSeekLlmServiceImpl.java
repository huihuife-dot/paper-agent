package com.myagent.assistant.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.function.Consumer;

/**
 * DeepSeek 大模型调用实现。
 *
 * 只负责读取 DeepSeek 配置和声明 provider，
 * 具体 HTTP 调用交给 OpenAiCompatibleLlmClient 复用。
 */
@Service
@ConditionalOnProperty(name = "llm.provider", havingValue = "deepseek")
public class DeepSeekLlmServiceImpl implements LlmService {

    private final String model;
    private final OpenAiCompatibleLlmClient client;

    public DeepSeekLlmServiceImpl(
            @Value("${deepseek.api.key}") String apiKey,
            @Value("${deepseek.base-url}") String baseUrl,
            @Value("${deepseek.model}") String model,
            @Value("${llm.max-tokens}") Integer maxTokens,
            @Value("${llm.temperature}") Double temperature,
            ObjectMapper objectMapper
    ) {
        this.model = model;
        this.client = new OpenAiCompatibleLlmClient(
                "DeepSeek",
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
        return "deepseek";
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
