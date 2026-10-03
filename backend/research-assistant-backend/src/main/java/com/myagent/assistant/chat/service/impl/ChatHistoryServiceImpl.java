package com.myagent.assistant.chat.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myagent.assistant.chat.entity.ChatMessage;
import com.myagent.assistant.chat.entity.ChatSession;
import com.myagent.assistant.chat.mapper.ChatMessageMapper;
import com.myagent.assistant.chat.mapper.ChatSessionMapper;
import com.myagent.assistant.chat.service.ChatHistoryService;
import com.myagent.assistant.rag.dto.RagSource;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 对话历史服务实现。
 */
@Service
public class ChatHistoryServiceImpl implements ChatHistoryService {

    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final ObjectMapper objectMapper;

    public ChatHistoryServiceImpl(ChatSessionMapper chatSessionMapper,
                                  ChatMessageMapper chatMessageMapper,
                                  ObjectMapper objectMapper) {
        this.chatSessionMapper = chatSessionMapper;
        this.chatMessageMapper = chatMessageMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public ChatSession createSession(String title, Long paperId) {
        ChatSession session = new ChatSession();
        session.setTitle(title != null && !title.isBlank() ? title : "新的对话");
        session.setPaperId(paperId);
        session.setCreateTime(LocalDateTime.now());
        session.setUpdateTime(LocalDateTime.now());

        chatSessionMapper.insert(session);
        return session;
    }

    @Override
    public List<ChatSession> listSessions() {
        QueryWrapper<ChatSession> wrapper = new QueryWrapper<>();
        wrapper.orderByDesc("update_time");
        return chatSessionMapper.selectList(wrapper);
    }

    @Override
    public List<ChatMessage> listMessages(Long sessionId) {
        if (sessionId == null) {
            throw new RuntimeException("会话 ID 不能为空");
        }

        QueryWrapper<ChatMessage> wrapper = new QueryWrapper<>();
        wrapper.eq("session_id", sessionId);
        wrapper.orderByAsc("id");
        return chatMessageMapper.selectList(wrapper);
    }

    @Override
    public List<ChatMessage> listRecentMessages(Long sessionId, int limit) {
        if (sessionId == null || limit < 1 || limit > 201) {
            throw new IllegalArgumentException("会话 ID 或历史查询条数不合法");
        }
        if (chatSessionMapper.selectById(sessionId) == null) throw new IllegalArgumentException("会话不存在");
        List<ChatMessage> messages = new java.util.ArrayList<>(chatMessageMapper.selectList(
                new QueryWrapper<ChatMessage>().eq("session_id", sessionId)
                        .orderByDesc("id").last("LIMIT " + limit)));
        java.util.Collections.reverse(messages);
        return messages;
    }

    @Override
    public void deleteSession(Long sessionId) {
        if (sessionId == null) {
            throw new RuntimeException("会话 ID 不能为空");
        }

        ChatSession session = chatSessionMapper.selectById(sessionId);
        if (session == null) {
            throw new RuntimeException("会话不存在");
        }

        QueryWrapper<ChatMessage> messageWrapper = new QueryWrapper<>();
        messageWrapper.eq("session_id", sessionId);
        chatMessageMapper.delete(messageWrapper);
        chatSessionMapper.deleteById(sessionId);
    }

    @Override
    public Long saveRagChat(Long sessionId,
                            Long paperId,
                            String question,
                            String answer,
                            String modelProvider,
                            String modelName,
                            List<RagSource> sources) {
        if (question == null || question.isBlank()) {
            throw new RuntimeException("问题不能为空");
        }

        ChatSession session;
        if (sessionId == null) {
            session = createSession(buildTitle(question), paperId);
            sessionId = session.getId();
        } else {
            session = chatSessionMapper.selectById(sessionId);
            if (session == null) {
                throw new RuntimeException("会话不存在");
            }
        }

        ChatMessage userMessage = new ChatMessage();
        userMessage.setSessionId(sessionId);
        userMessage.setRole("user");
        userMessage.setContent(question);
        userMessage.setCreateTime(LocalDateTime.now());
        chatMessageMapper.insert(userMessage);

        ChatMessage assistantMessage = new ChatMessage();
        assistantMessage.setSessionId(sessionId);
        assistantMessage.setRole("assistant");
        assistantMessage.setContent(answer);
        assistantMessage.setModelProvider(modelProvider);
        assistantMessage.setModelName(modelName);
        assistantMessage.setSourcesJson(toJson(sources));
        assistantMessage.setCreateTime(LocalDateTime.now());
        chatMessageMapper.insert(assistantMessage);

        session.setUpdateTime(LocalDateTime.now());
        chatSessionMapper.updateById(session);

        return sessionId;
    }

    /**
     * 默认用问题前 30 个字符作为会话标题。
     */
    private String buildTitle(String question) {
        String text = question.replaceAll("\\s+", " ").trim();
        return text.length() <= 30 ? text : text.substring(0, 30);
    }

    /**
     * 把 sources 保存为 JSON 字符串。
     *
     * 这里保存的是摘要，方便后续前端展示“回答参考了哪些文献片段”。
     */
    private String toJson(List<RagSource> sources) {
        try {
            return objectMapper.writeValueAsString(sources);
        } catch (Exception e) {
            throw new RuntimeException("保存 RAG sources 失败：" + e.getMessage(), e);
        }
    }
}
