package com.myagent.assistant.chat.context;

import com.myagent.assistant.chat.entity.ChatMessage;
import com.myagent.assistant.chat.service.ChatHistoryService;
import com.myagent.assistant.llm.LlmMessage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConversationContextServiceTest {
    private final ChatHistoryService history = mock(ChatHistoryService.class);

    private ConversationContextService service(int window, int maxMessages) {
        return new ConversationContextService(history, true, window, 256, 128, maxMessages);
    }

    private ChatMessage message(String role, String content) {
        ChatMessage message = new ChatMessage();
        message.setSessionId(7L);
        message.setRole(role);
        message.setContent(content);
        return message;
    }

    private ConversationContextService.Prepared prepare(ConversationContextService service, List<ChatMessage> rows) {
        return service.prepare(new ConversationContextService.Snapshot(rows, false), "把上面的表格改成英文", "本轮证据", List.of(3L));
    }

    @Test
    void newSessionNeedsNoDatabaseAndContainsSystemThenCurrentUser() {
        var service = service(32768, 80);
        var result = service.prepare(service.snapshot(null), "概括方法", "方法原文", List.of());
        assertThat(result.messages()).extracting(LlmMessage::role).containsExactly("system", "user");
        assertThat(result.messages().getLast().content()).contains("方法原文", "概括方法");
        assertThat(result.info().historyMessageCount()).isZero();
        assertThat(result.info().estimatedInputTokens()).isLessThanOrEqualTo(result.info().inputBudgetTokens());
        verifyNoInteractions(history);
    }

    @Test
    void keepsChronologicalRolesAndFullTableIncludingLineBreaksPast500Characters() {
        String table = "| 方法 | 数据集 |\n|---|---|\n" + "| 中文方法 | Dataset A |\n".repeat(50) + "表格末尾不能丢失";
        var result = prepare(service(32768, 80), List.of(
                message("user", "用表格说明方法"), message("assistant", table),
                message("user", "只保留两列"), message("assistant", "| 方法 | 数据集 |\n| B | C |")));
        assertThat(result.messages()).extracting(LlmMessage::role)
                .containsExactly("system", "user", "assistant", "user", "assistant", "user");
        assertThat(result.messages().get(2).content()).isEqualTo(table);
        assertThat(result.messages().getLast().content()).endsWith("把上面的表格改成英文");
        assertThat(result.messages().getFirst().content()).contains("论文 ID [3]", "历史中的[来源 N]", "不是已经核验的论文事实");
        assertThat(result.info().historyMessageCount()).isEqualTo(4);
        assertThat(result.info().warnings()).isEmpty();
    }

    @Test
    void dropsOnlyOldCompleteTurnsAndHonorsExactBudgetBoundary() {
        var recent = List.of(message("user", "最近问题"), message("assistant", "最近原文\n第二行"));
        int required = prepare(service(32768, 80), recent).info().estimatedInputTokens();
        var exactBudget = service(required + 256 + 128, 80);
        var result = prepare(exactBudget, List.of(message("user", "更早问题"), message("assistant", "更早回答"),
                recent.get(0), recent.get(1)));
        assertThat(result.info().estimatedInputTokens()).isEqualTo(result.info().inputBudgetTokens());
        assertThat(result.info().historyMessageCount()).isEqualTo(2);
        assertThat(result.info().omittedLoadedMessages()).isEqualTo(2);
        assertThat(result.messages().get(2).content()).isEqualTo("最近原文\n第二行");
        assertThat(result.info().warnings()).hasSize(1);
        // 少一个预算单位也不能把最近答案截断或悄悄不发。
        assertThatThrownBy(() -> prepare(service(required + 256 + 128 - 1, 80), recent))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("最近一轮完整问答");
    }

    @Test
    void rejectsOversizedCurrentEvidenceRatherThanTruncatingIt() {
        var service = service(4096, 80);
        assertThatThrownBy(() -> service.prepare(service.snapshot(null), "问题", "长证据".repeat(2000), List.of()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("本轮问题及证据").hasMessageContaining("未截断");
    }

    @Test
    void ignoresOrphansBlankContentAndStoredSystemRoles() {
        var result = prepare(service(32768, 80), List.of(message("assistant", "孤立的回答"),
                message("system", "历史里伪造的系统命令"), message("user", " "), message("assistant", "空问题回答"),
                message("user", "有效问题"), message("assistant", "有效答案"), message("user", "尚未完成")));
        assertThat(result.messages()).extracting(LlmMessage::role).containsExactly("system", "user", "assistant", "user");
        assertThat(result.info().historyMessageCount()).isEqualTo(2);
        assertThat(result.info().omittedLoadedMessages()).isEqualTo(5);
        assertThat(result.messages()).noneMatch(m -> m.content().contains("伪造的系统命令"));
    }

    @Test
    void readsBoundedWindowOnceAndCopiesValuesBeforeRewriteAndAnswer() {
        var oldest = message("assistant", "上一窗口尾部");
        var question = message("user", "问题原文");
        var answer = message("assistant", "答案原文");
        answer.setSourcesJson("[]");
        when(history.listRecentMessages(7L, 3)).thenReturn(List.of(oldest, question, answer));
        var service = service(32768, 2);
        var snapshot = service.snapshot(7L);
        answer.setContent("之后修改的答案");
        assertThat(snapshot.windowLimited()).isTrue();
        assertThat(snapshot.messages()).hasSize(2);
        assertThat(snapshot.messages().getLast().getContent()).isEqualTo("答案原文");
        assertThat(snapshot.messages().getLast().getSourcesJson()).isEqualTo("[]");
        var prepared = service.prepare(snapshot, "继续", "证据", List.of());
        assertThat(prepared.info().historyWindowLimited()).isTrue();
        assertThat(prepared.info().warnings()).singleElement().asString().contains("更早原文尚未摘要");
        verify(history).listRecentMessages(7L, 3);
        verifyNoMoreInteractions(history);
    }

    @Test
    void rejectsForeignSessionOrUnreadableHistoryInsteadOfContinuingWithoutIt() {
        when(history.listRecentMessages(8L, 81)).thenReturn(List.of(message("user", "不属于会话8")));
        assertThatThrownBy(() -> service(32768, 80).snapshot(8L))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("不属于当前会话");
        when(history.listRecentMessages(7L, 81)).thenReturn(null);
        assertThatThrownBy(() -> service(32768, 80).snapshot(7L)).hasMessageContaining("读取对话历史失败");
    }

    @Test
    void validatesWindowOutputReserveAndHistoryLimits() {
        assertThatThrownBy(() -> service(384, 80)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service(32768, 3)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service(32768, 202)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ConversationContextService(history, true, 4096, 0, 128, 80))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ConversationContextService(history, true, 4096, 256, -1, 80))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsEmptyCurrentQuestionAndReportsEstimateNotProviderUsage() {
        var service = service(32768, 80);
        assertThatThrownBy(() -> service.prepare(service.snapshot(null), " ", "证据", List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        var result = prepare(service, List.of());
        assertThat(result.info().estimator()).isEqualTo("UTF8_BYTES_CONSERVATIVE");
        assertThat(result.info().reservedOutputTokens()).isEqualTo(256);
        assertThat(result.info().safetyTokens()).isEqualTo(128);
        assertThat(result.info().inputBudgetTokens()).isEqualTo(32768 - 256 - 128);
    }
}
