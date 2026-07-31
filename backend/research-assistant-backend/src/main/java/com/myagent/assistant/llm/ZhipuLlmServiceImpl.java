package com.myagent.assistant.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.function.Consumer;

/**
 * 智谱 GLM 大模型调用实现。
 *
 * 使用智谱 GLM v4 Chat Completions 风格接口：
 * https://open.bigmodel.cn/api/paas/v4/chat/completions
 */
@Service
@ConditionalOnProperty(name = "llm.provider", havingValue = "zhipu")
public class ZhipuLlmServiceImpl implements LlmService {

    private final String model;
    private final OpenAiCompatibleLlmClient client;

    public ZhipuLlmServiceImpl(
            @Value("${zhipu.api.key}") String apiKey,
            @Value("${zhipu.base-url}") String baseUrl,
            @Value("${zhipu.model}") String model,
            @Value("${llm.max-tokens}") Integer maxTokens,
            @Value("${llm.temperature}") Double temperature,
            ObjectMapper objectMapper
    ) {
        this.model = model;
        this.client = new OpenAiCompatibleLlmClient(
                "Zhipu GLM",
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
        return "zhipu";
    }

    @Override
    public String modelName() {
        return model;
    }
}
