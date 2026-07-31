package com.myagent.assistant.rag.dto;

import lombok.Data;

/**
 * 一次 RAG 问答的分段耗时，单位均为毫秒。
 *
 * 计时仅用于诊断，不参与检索排序或回答生成。
 */
@Data
public class RagTiming {

    private Long strategySelectionMs;
    private Long queryRewriteMs;
    private Long embeddingMs;
    private Long vectorSearchMs;
    private Long sourceHydrationMs;
    private Long bm25Ms;
    private Long fusionRankingMs;
    private Long contextBuildMs;
    private Long promptBuildMs;
    private Long llmGenerationMs;
    private Long firstTokenMs;
    private Long firstContentMs;
    private Long historySaveMs;
    private Long postProcessingMs;
    private Long otherMs;
    private Long totalMs;
    private String dominantStage;
}
