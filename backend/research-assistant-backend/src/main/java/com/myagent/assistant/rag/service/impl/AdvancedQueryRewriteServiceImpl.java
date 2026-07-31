package com.myagent.assistant.rag.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myagent.assistant.llm.LlmService;
import com.myagent.assistant.rag.dto.QueryRewriteResult;
import com.myagent.assistant.rag.service.AdvancedQueryRewriteService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 高级 Query Rewrite 实现。
 *
 * 单次 LLM 调用同时完成：
 * 1. 疑问句→学术陈述句改写
 * 2. 核心技术关键词提取
 * 3. HyDE 假设性答案片段生成
 */
@Service
public class AdvancedQueryRewriteServiceImpl implements AdvancedQueryRewriteService {

    private static final Logger log = LoggerFactory.getLogger(AdvancedQueryRewriteServiceImpl.class);

    private final LlmService llmService;
    private final ObjectMapper objectMapper;

    public AdvancedQueryRewriteServiceImpl(LlmService llmService, ObjectMapper objectMapper) {
        this.llmService = llmService;
        this.objectMapper = objectMapper;
    }

    @Override
    public QueryRewriteResult rewrite(String question) {
        if (question == null || question.isBlank()) {
            throw new RuntimeException("问题不能为空");
        }

        QueryRewriteResult result = new QueryRewriteResult();
        result.setOriginalQuestion(question);

        try {
            String prompt = buildRewritePrompt(question);
            String llmOutput = llmService.generateAnswer(prompt);
            result.setRawLlmOutput(llmOutput);

            if (llmOutput == null || llmOutput.isBlank()) {
                log.warn("AdvancedQueryRewrite LLM 返回为空，使用原始问题兜底");
                return buildFallback(question);
            }

            String json = extractJson(llmOutput);
            QueryRewriteResult parsed = objectMapper.readValue(json, QueryRewriteResult.class);
            parsed.setOriginalQuestion(question);
            parsed.setRawLlmOutput(llmOutput);
            return parsed;

        } catch (Exception e) {
            log.warn("AdvancedQueryRewrite LLM 解析失败，使用原始问题兜底。LLM 输出前 500 字符: {}",
                    result.getRawLlmOutput() == null ? "null"
                            : result.getRawLlmOutput().substring(0, Math.min(result.getRawLlmOutput().length(), 500)),
                    e);
            return buildFallback(question);
        }
    }

    /**
     * 构造改写 prompt。
     *
     * 要求 LLM 一次完成陈述句改写、关键词提取和 HyDE 生成，
     * 避免多次调用增加延迟。
     */
    private String buildRewritePrompt(String question) {
        return """
                你是论文检索 query 改写助手。请根据下面的用户问题，完成以下三件事：

                1. **陈述句改写**（declarativeQuery）：
                   把疑问句改写成学术英文陈述句。
                   去掉"有没有"、"哪篇"、"是什么"等疑问词，
                   保留核心技术术语和缩写，
                   使用论文摘要中常见的表达方式。
                   示例："有没有论文用Transformer做时间序列预测？"
                   → "Transformer-based architectures for time series forecasting"

                2. **关键词提取**（keywordQuery）：
                   从问题中提取 3~7 个核心技术关键词或短语，
                   用空格拼接成一个检索用字符串。
                   示例："Transformer attention mechanism time series forecasting wind speed"

                3. **HyDE 生成**（hydeQuery）：
                   假设存在一篇完美回答该问题的论文，
                   用 2~4 句学术英文写下它的摘要片段。
                   这段文字会被嵌入到向量空间中进行检索，
                   因此应包含可能出现在真实论文中的术语和表述。
                   示例："This paper proposes a novel Transformer-based architecture
                   for wind speed forecasting. The model integrates attention mechanisms
                   with temporal convolutional networks to capture both long-range
                   dependencies and local patterns in meteorological time series data."

                要求：
                - 所有字段值都必须是字符串，严禁使用 JSON 数组。
                - 只返回 JSON，不要用 ``` 包裹，不要返回 Markdown。
                - 如果问题已经是英文，仍需要做陈述句改写和关键词提取。

                用户问题：
                %s

                JSON 格式：
                {
                  "declarativeQuery": "陈述句改写",
                  "keywordQuery": "关键词1 关键词2 关键词3",
                  "keywordTerms": "关键词1, 关键词2, 关键词3",
                  "hydeQuery": "假设性答案片段"
                }
                """.formatted(question);
    }

    /**
     * 从 LLM 输出中提取 JSON 对象。
     */
    private String extractJson(String text) {
        int start = text.indexOf("{");
        int end = text.lastIndexOf("}");
        if (start < 0 || end < 0 || end <= start) {
            throw new RuntimeException("大模型返回内容不是 JSON 对象");
        }
        return text.substring(start, end + 1);
    }

    /**
     * LLM 调用失败时，用原始问题构造兜底结果。
     */
    private QueryRewriteResult buildFallback(String question) {
        QueryRewriteResult fallback = new QueryRewriteResult();
        fallback.setOriginalQuestion(question);
        fallback.setDeclarativeQuery(question);
        fallback.setKeywordQuery(null);
        fallback.setHydeQuery(null);
        return fallback;
    }
}
