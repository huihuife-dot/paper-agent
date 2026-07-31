package com.myagent.assistant.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiCompatibleLlmClientTest {

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
