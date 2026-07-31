package com.myagent.assistant.chat.service;

import com.myagent.assistant.chat.entity.ChatMessage;
import com.myagent.assistant.chat.entity.ChatSession;
import com.myagent.assistant.rag.dto.RagSource;

import java.util.List;

public interface ChatHistoryService {

    /**
     * 创建一个新的对话会话。
     */
    ChatSession createSession(String title, Long paperId);

    /**
     * 查询所有会话，按更新时间倒序。
     */
    List<ChatSession> listSessions();

    /**
     * 查询某个会话下的消息，按创建时间正序。
     */
    List<ChatMessage> listMessages(Long sessionId);

    /**
     * 删除会话及其消息。
     */
    void deleteSession(Long sessionId);

    /**
     * 保存一次 RAG 问答。
     *
     * 如果 sessionId 为空，则自动创建新会话。
     *
     * @return 最终使用的 sessionId
     */
    Long saveRagChat(
            Long sessionId,
            Long paperId,
            String question,
            String answer,
            String modelProvider,
            String modelName,
            List<RagSource> sources
    );
}