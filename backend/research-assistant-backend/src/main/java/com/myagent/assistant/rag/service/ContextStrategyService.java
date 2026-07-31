package com.myagent.assistant.rag.service;

import com.myagent.assistant.rag.context.ContextStrategy;

import java.util.List;

/**
 * RAG 上下文策略选择服务。
 */
public interface ContextStrategyService {

    /**
     * 根据本轮有效论文范围选择上下文策略。
     *
     * @param paperIds 本轮限定的论文 ID；为空或多篇时默认回退 VECTOR_RAG
     * @return 上下文策略
     */
    ContextStrategy chooseStrategy(List<Long> paperIds);
}
