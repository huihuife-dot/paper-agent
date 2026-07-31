package com.myagent.assistant.rag.service;

import com.myagent.assistant.rag.context.HybridRagContext;
import com.myagent.assistant.rag.context.HybridRagContextRequest;

/**
 * 多篇论文混合 RAG 上下文构造服务。
 */
public interface HybridRagContextService {

    /**
     * 根据文献画像、章节摘要和少量原文证据构造多篇论文上下文。
     */
    HybridRagContext buildContext(HybridRagContextRequest request);
}
