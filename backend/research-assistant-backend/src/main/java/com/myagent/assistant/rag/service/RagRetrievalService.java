package com.myagent.assistant.rag.service;

import com.myagent.assistant.rag.dto.QueryRewriteResult;
import com.myagent.assistant.rag.dto.RagSource;

import java.util.List;

/**
 * RAG 检索服务。
 *
 * 负责根据用户问题检索相关文献片段。
 * 当前阶段只做 retrieval，不调用大模型生成回答。
 */
public interface RagRetrievalService {

    /**
     * 根据用户问题检索相关 chunk 来源。
     *
     * @param question 用户问题
     * @param topK     返回前 K 个来源
     * @return RAG sources
     */
    List<RagSource> retrieveSources(String question, Integer topK);

    /**
     * 根据用户问题和可选文献范围检索相关 chunk 来源。
     *
     * @param question 用户问题
     * @param topK     返回前 K 个来源
     * @param paperIds 限定检索的文献 ID 列表；为空时检索全部文献
     * @return RAG sources
     */
    List<RagSource> retrieveSources(String question, Integer topK, List<Long> paperIds);

    /**
     * 使用高级 Query Rewrite 结果进行多路检索 + RRF 融合。
     *
     * 将疑问句改写、关键词、HyDE 三路 query 分别检索，
     * 然后用 RRF（Reciprocal Rank Fusion）合并去重。
     *
     * @param rewriteResult 高级 Query Rewrite 结果
     * @param topK          最终返回的 source 数量
     * @param paperIds      限定检索的文献 ID 列表；为空时检索全部文献
     * @return RRF 融合并排序后的 sources
     */
    List<RagSource> retrieveSourcesWithRewrite(QueryRewriteResult rewriteResult, Integer topK, List<Long> paperIds);
}