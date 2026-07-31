package com.myagent.assistant.rag.context;

/**
 * RAG 上下文策略。
 */
public enum ContextStrategy {
    /**
     * 当前已有向量检索 RAG：问题 embedding -> Qdrant topK -> chunk prompt。
     */
    VECTOR_RAG,

    /**
     * 单篇论文全文解析上下文：按章节组织 MySQL 中的结构化 chunk。
     */
    FULL_TEXT_PARSED,

    /**
     * 多篇论文混合上下文：组合文献画像、章节摘要和少量原文证据。
     */
    HYBRID_RAG,

    /**
     * 全库文献发现模式：不指定论文，改写 query 后多路检索、RRF 融合、按论文聚合。
     */
    LIBRARY_DISCOVERY
}
