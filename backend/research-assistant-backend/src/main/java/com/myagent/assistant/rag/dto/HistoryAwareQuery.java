package com.myagent.assistant.rag.dto;

/**
 * 多轮追问解析结果。
 */
public record HistoryAwareQuery(
        String originalQuestion,
        String retrievalQuestion,
        boolean rewritten,
        int historyMessageCount
) {
}
