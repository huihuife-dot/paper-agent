package com.myagent.assistant.rag.service;

import com.myagent.assistant.rag.dto.PaperRelevance;
import com.myagent.assistant.rag.dto.PaperCandidate;
import com.myagent.assistant.rag.dto.RagSource;

import java.util.List;

/**
 * 论文发现服务。
 *
 * 将全库检索返回的扁平 chunk 列表按论文聚合，
 * 计算每篇论文的相关度分数，并返回排序后的论文相关度列表。
 */
public interface PaperDiscoveryService {

    /**
     * 按论文聚合检索结果。
     *
     * @param sources 全库检索返回的 sources（可能来自多篇论文）
     * @param topPapers 最多返回论文数
     * @return 按相关度降序排列的论文相关度列表
     */
    List<PaperRelevance> aggregateByPaper(List<RagSource> sources, int topPapers);

    /**
     * 将分类型检索证据聚合为论文级候选排序。
     *
     * 每种 sourceType 先独立归一化，避免画像数量少、原文块数量多导致的分数不可比；
     * 同一论文在多种粒度都有命中时获得覆盖加分。
     */
    List<Long> rankPaperIdsByEvidence(List<RagSource> sources, int topPapers);

    /**
     * 返回带可解释分数的论文候选，用于动态候选截止。
     */
    List<PaperCandidate> rankCandidatesByEvidence(List<RagSource> sources,
                                                  String question,
                                                  List<String> queryTerms,
                                                  int topPapers);
}
