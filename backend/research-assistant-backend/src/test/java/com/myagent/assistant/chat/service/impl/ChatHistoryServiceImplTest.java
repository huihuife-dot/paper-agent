package com.myagent.assistant.chat.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.myagent.assistant.chat.entity.ChatSession;
import com.myagent.assistant.chat.mapper.ChatMessageMapper;
import com.myagent.assistant.chat.mapper.ChatSessionMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatHistoryServiceImplTest {

    @Test
    void deleteSessionDeletesMessagesBeforeDeletingSession() {
        ChatSessionMapper chatSessionMapper = mock(ChatSessionMapper.class);
        ChatMessageMapper chatMessageMapper = mock(ChatMessageMapper.class);
        ChatSession session = new ChatSession();
        session.setId(7L);

        when(chatSessionMapper.selectById(7L)).thenReturn(session);

        ChatHistoryServiceImpl service = new ChatHistoryServiceImpl(
                chatSessionMapper,
                chatMessageMapper,
                new ObjectMapper()
        );

        service.deleteSession(7L);

        verify(chatMessageMapper).delete(any(Wrapper.class));
        verify(chatSessionMapper).deleteById(7L);
    }

    @Test
    void deleteSessionRejectsMissingSession() {
        ChatSessionMapper chatSessionMapper = mock(ChatSessionMapper.class);
        ChatMessageMapper chatMessageMapper = mock(ChatMessageMapper.class);

        when(chatSessionMapper.selectById(99L)).thenReturn(null);

        ChatHistoryServiceImpl service = new ChatHistoryServiceImpl(
                chatSessionMapper,
                chatMessageMapper,
                new ObjectMapper()
        );

        assertThatThrownBy(() -> service.deleteSession(99L))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("会话不存在");

        verify(chatMessageMapper, never()).delete(any(Wrapper.class));
        verify(chatSessionMapper, never()).deleteById(99L);
    }
}
