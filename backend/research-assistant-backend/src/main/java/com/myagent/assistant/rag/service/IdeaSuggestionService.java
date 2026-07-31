package com.myagent.assistant.rag.service;

import com.myagent.assistant.rag.dto.RagSource;

import java.util.List;

/**
 * Research Idea 建议服务。
 *
 * 用于判断某次 RAG 问答是否值得提醒用户保留为 Research Idea。
 */
public interface IdeaSuggestionService {

    /**
     * 判断是否建议保留为 Research Idea。
     *
     * @param question 用户问题
     * @param answer 大模型回答
     * @param sources 本次 RAG 检索来源
     * @return 是否建议保留
     */
    boolean shouldSuggestSaveAsIdea(String question, String answer, List<RagSource> sources);

    /**
     * 生成建议原因。
     *
     * @param question 用户问题
     * @param answer 大模型回答
     * @param sources 本次 RAG 检索来源
     * @return 建议原因
     */
    String buildSuggestionReason(String question, String answer, List<RagSource> sources);
}