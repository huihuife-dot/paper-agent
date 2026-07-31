package com.myagent.assistant.rag.service;

import com.myagent.assistant.rag.dto.RagStreamMetadata;

/**
 * RAG 流式执行事件监听器。
 */
public interface RagStreamListener {

    void onMetadata(RagStreamMetadata metadata);

    void onDelta(String content);
}
