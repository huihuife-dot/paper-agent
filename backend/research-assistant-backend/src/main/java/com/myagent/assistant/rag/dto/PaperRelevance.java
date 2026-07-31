package com.myagent.assistant.rag.dto;

import lombok.Data;

import java.util.List;

/**
 * 论文相关度聚合结果。
 *
 * 全库检索时，将命中的 chunks 按论文聚合，
 * 返回每篇论文的命中统计和最相关片段。
 */
@Data
public class PaperRelevance {

    /**
     * 论文 ID。
     */
    private Long paperId;

    /**
     * 论文标题。
     */
    private String paperTitle;

    /**
     * 该论文在全库检索中命中的 chunk 数量。
     */
    private int hitCount;

    /**
     * 命中 chunk 的平均 Qdrant 相似度。
     */
    private Double avgScore;

    /**
     * 命中 chunk 的最高 Qdrant 相似度。
     */
    private Double maxScore;

    /**
     * 该论文最相关的 2 个 chunk。
     */
    private List<RagSource> topChunks;
}
