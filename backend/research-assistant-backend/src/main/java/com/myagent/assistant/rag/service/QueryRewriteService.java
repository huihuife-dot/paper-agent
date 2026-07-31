package com.myagent.assistant.rag.service;

/**
 * RAG 检索问题改写服务。
 *
 * 用于解决“中文问题检索英文论文 chunk”时召回不稳定的问题。
 */
public interface QueryRewriteService {

    /**
     * 将用户原始问题改写为更适合向量检索的 query。
     *
     * 当前策略：
     * 1. 英文问题：原样返回
     * 2. 中文问题：翻译成英文检索 query
     *
     * @param question 用户原始问题
     * @return 用于 embedding 检索的问题
     */
    String rewriteForRetrieval(String question);
}