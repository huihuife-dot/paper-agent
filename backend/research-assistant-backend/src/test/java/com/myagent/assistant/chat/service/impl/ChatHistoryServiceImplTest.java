package com.myagent.assistant.chat.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.myagent.assistant.chat.entity.ChatMessage;
import com.myagent.assistant.chat.entity.ChatSession;
import com.myagent.assistant.chat.mapper.ChatMessageMapper;
import com.myagent.assistant.chat.mapper.ChatSessionMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatHistoryServiceImplTest {

    @Test
    void recentHistoryIsBoundedToSessionAndRestoredToAscendingIdOrder() {
        var sessions = mock(ChatSessionMapper.class);
        var messages = mock(ChatMessageMapper.class);
        when(sessions.selectById(7L)).thenReturn(new ChatSession());
        ChatMessage older = new ChatMessage(); older.setId(10L);
        ChatMessage newer = new ChatMessage(); newer.setId(11L);
        when(messages.selectList(any(Wrapper.class))).thenReturn(List.of(newer, older));
        var service = new ChatHistoryServiceImpl(sessions, messages, new ObjectMapper());
        assertThat(service.listRecentMessages(7L, 81)).extracting(ChatMessage::getId).containsExactly(10L, 11L);
        ArgumentCaptor<QueryWrapper<ChatMessage>> query = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(messages).selectList(query.capture());
        assertThat(query.getValue().getSqlSegment()).contains("session_id =", "ORDER BY id DESC", "LIMIT 81");
        assertThat(query.getValue().getParamNameValuePairs()).containsValue(7L);
    }

    @Test
    void invalidLimitAndMissingSessionAreNotTreatedAsEmptyHistory() {
        var sessions = mock(ChatSessionMapper.class);
        var messages = mock(ChatMessageMapper.class);
        var service = new ChatHistoryServiceImpl(sessions, messages, new ObjectMapper());
        assertThatThrownBy(() -> service.listRecentMessages(7L, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.listRecentMessages(7L, 202)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.listRecentMessages(null, 80)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.listRecentMessages(99L, 81)).hasMessage("会话不存在");
        verify(messages, never()).selectList(any(Wrapper.class));
    }

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
