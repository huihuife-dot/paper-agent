package com.myagent.assistant.llm;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;


/**
 * 大模型服务的占位实现。
 *
 * 当前阶段暂不调用真实大模型 API，
 * 只用于验证 RAG 主流程已经能通过 LlmService 获取 answer。
 */
@Service
@ConditionalOnProperty(name = "llm.provider", havingValue = "fake", matchIfMissing = true)
public class FakeLlmServiceImpl implements LlmService {

    @Override
    public String generateAnswer(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            throw new RuntimeException("Prompt 不能为空");
        }

        return "当前阶段已完成 RAG Prompt 构造，并已通过 LlmService 生成占位回答。后续接入真实大模型后，这里会返回模型生成的正式回答。";
    }


    @Override
    public String provider() {
        return "fake";
    }

    @Override
    public String generateMessages(java.util.List<LlmMessage> messages) {
        if (messages == null || messages.isEmpty()) throw new IllegalArgumentException("消息列表不能为空");
        return generateAnswer(messages.get(messages.size() - 1).content());
    }

    @Override
    public String modelName() {
        return "fake-llm";
    }
}
