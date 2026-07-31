package com.myagent.assistant.rag.service;

import com.myagent.assistant.rag.dto.RagChatRequest;
import com.myagent.assistant.rag.dto.RagChatResponse;

/**
 * RAG 问答服务。
 */
public interface RagChatService {

    /**
     * 基于文献 sources 进行问答。
     *
     * 当前阶段先返回占位 answer；
     * 后续接入真实大模型后，再在这里调用 LLM。
     *
     * @param request RAG 问答请求
     * @return RAG 问答响应
     */
    RagChatResponse chat(RagChatRequest request);

    /**
     * 使用同一套 RAG 链路流式生成回答。
     */
    RagChatResponse chatStream(RagChatRequest request, RagStreamListener listener);
}
