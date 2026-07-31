package com.myagent.assistant.rag.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.myagent.assistant.idea.dto.FlexibleStringDeserializer;
import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 高级 Query Rewrite 结果。
 *
 * 包含疑问句→陈述句改写、关键词提取、HyDE 假设性答案三路输出，
 * 以及对 LLM 返回 JSON 解析失败的兜底。
 */
@Data
public class QueryRewriteResult {

    /**
     * 用户原始问题。
     */
    private String originalQuestion;

    /**
     * 陈述句改写结果（主力检索 query）。
     *
     * 例如："哪篇论文用了Transformer做风速预测？"
     * 改写为："Transformer architecture applied to wind speed forecasting"
     */
    private String declarativeQuery;

    /**
     * 关键词检索 query。
     *
     * 从问题中提取的核心技术关键词拼接而成，用于精确匹配。
     * 大模型可能返回 JSON 数组，使用 FlexibleStringDeserializer 兜底。
     */
    @JsonDeserialize(using = FlexibleStringDeserializer.class)
    private String keywordQuery;

    /**
     * HyDE（假设性文档嵌入）query。
     *
     * 生成一段假设性答案片段，其 embedding 通常比问题 embedding 更接近真实相关 chunk。
     */
    private String hydeQuery;

    /**
     * 提取的关键词列表。
     */
    @JsonDeserialize(using = FlexibleStringDeserializer.class)
    private String keywordTerms;

    /**
     * 大模型原始返回文本（调试用）。
     */
    private String rawLlmOutput;

    /**
     * 所有去重后的检索 query 列表。
     *
     * 包含 declarativeQuery、keywordQuery、hydeQuery 中的非空、非重复值。
     */
    public List<String> allQueries() {
        Set<String> seen = new LinkedHashSet<>();
        List<String> queries = new ArrayList<>();

        addIfNotBlank(queries, seen, declarativeQuery);
        addIfNotBlank(queries, seen, keywordQuery);
        addIfNotBlank(queries, seen, hydeQuery);

        // 如果 LLM 全部失败，至少用原始问题兜底
        if (queries.isEmpty() && originalQuestion != null && !originalQuestion.isBlank()) {
            queries.add(originalQuestion);
        }

        return queries;
    }

    /**
     * 是否有效（至少能生成一个改写 query）。
     */
    public boolean isValid() {
        return !allQueries().isEmpty();
    }

    private void addIfNotBlank(List<String> list, Set<String> seen, String value) {
        if (value != null && !value.isBlank() && seen.add(value.strip())) {
            list.add(value.strip());
        }
    }
}
