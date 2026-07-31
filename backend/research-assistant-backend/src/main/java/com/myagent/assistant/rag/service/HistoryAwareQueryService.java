package com.myagent.assistant.rag.service;

import com.myagent.assistant.rag.dto.HistoryAwareQuery;

/**
 * 将依赖会话历史的追问解析为独立检索问题。
 */
public interface HistoryAwareQueryService {

    HistoryAwareQuery resolve(Long sessionId, String question);
}
