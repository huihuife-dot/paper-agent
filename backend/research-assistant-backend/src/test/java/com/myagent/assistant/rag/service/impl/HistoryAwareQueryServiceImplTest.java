package com.myagent.assistant.rag.service.impl;

import com.myagent.assistant.chat.entity.ChatMessage;
import com.myagent.assistant.chat.service.ChatHistoryService;
import com.myagent.assistant.llm.LlmService;
import com.myagent.assistant.rag.dto.HistoryAwareQuery;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HistoryAwareQueryServiceImplTest {

    @Test
    void keepsQuestionWhenSessionIsMissingOrPaperIsExplicit() {
        ChatHistoryService history = mock(ChatHistoryService.class);
        LlmService llm = mock(LlmService.class);
        HistoryAwareQueryServiceImpl service = new HistoryAwareQueryServiceImpl(history, llm);

        HistoryAwareQuery noSession = service.resolve(null, "它有什么局限？");
        HistoryAwareQuery explicit = service.resolve(9L, "论文13有什么局限？");

        assertThat(noSession.rewritten()).isFalse();
        assertThat(explicit.retrievalQuestion()).isEqualTo("论文13有什么局限？");
        verify(history, never()).listMessages(9L);
        verify(llm, never()).generateAnswer(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void rewritesContextDependentFollowUpUsingOnlyRecentMessages() {
        ChatHistoryService history = mock(ChatHistoryService.class);
        LlmService llm = mock(LlmService.class);
        HistoryAwareQueryServiceImpl service = new HistoryAwareQueryServiceImpl(history, llm);
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(message("user", "最早一轮不应进入窗口"));
        messages.add(message("assistant", "旧回答"));
        messages.add(message("user", "论文13的核心方法是什么？"));
        messages.add(message("assistant", "论文13提出BVMD-PAM-TWGCNet。"));
        messages.add(message("user", "它如何进行空间建模？"));
        messages.add(message("assistant", "它使用图卷积建模站点关系。"));
        messages.add(message("user", "论文15采用什么结构？"));
        messages.add(message("assistant", "论文15使用FFTransformer与GNN。"));
        when(history.listMessages(11L)).thenReturn(messages);
        when(llm.generateAnswer(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn("独立问题：论文15的 FFTransformer 与 GNN 方法有什么局限？");

        HistoryAwareQuery result = service.resolve(11L, "它有什么局限？");

        assertThat(result.rewritten()).isTrue();
        assertThat(result.historyMessageCount()).isEqualTo(6);
        assertThat(result.retrievalQuestion()).isEqualTo("论文15的 FFTransformer 与 GNN 方法有什么局限？");
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(llm).generateAnswer(prompt.capture());
        assertThat(prompt.getValue()).doesNotContain("最早一轮不应进入窗口");
        assertThat(prompt.getValue()).contains("当前追问：", "它有什么局限？");
    }

    @Test
    void fallsBackToOriginalQuestionWhenRewriteFails() {
        ChatHistoryService history = mock(ChatHistoryService.class);
        LlmService llm = mock(LlmService.class);
        HistoryAwareQueryServiceImpl service = new HistoryAwareQueryServiceImpl(history, llm);
        when(history.listMessages(12L)).thenReturn(List.of(message("user", "论文13的核心方法是什么？")));
        when(llm.generateAnswer(org.mockito.ArgumentMatchers.anyString()))
                .thenThrow(new RuntimeException("model unavailable"));

        HistoryAwareQuery result = service.resolve(12L, "还有什么改进方向？");

        assertThat(result.rewritten()).isFalse();
        assertThat(result.retrievalQuestion()).isEqualTo("还有什么改进方向？");
    }

    private ChatMessage message(String role, String content) {
        ChatMessage message = new ChatMessage();
        message.setRole(role);
        message.setContent(content);
        return message;
    }
}
