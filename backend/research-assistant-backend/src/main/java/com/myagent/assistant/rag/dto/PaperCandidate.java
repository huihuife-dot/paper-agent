package com.myagent.assistant.rag.dto;

/**
 * 全库发现阶段的论文级候选分数。
 *
 * @param paperId 论文ID
 * @param score 聚合后的候选分数
 * @param coveredSourceTypes 命中的内容粒度数量
 * @param queryTermCoverage 明确查询术语的覆盖率
 */
public record PaperCandidate(
        Long paperId,
        double score,
        int coveredSourceTypes,
        double queryTermCoverage
) {
}
