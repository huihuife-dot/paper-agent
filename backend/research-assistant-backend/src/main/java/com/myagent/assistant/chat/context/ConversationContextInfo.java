package com.myagent.assistant.chat.context;

import java.util.List;

/** 只返回预算与使用情况，不把整段私有历史额外暴露到执行详情。 */
public record ConversationContextInfo(int historyMessageCount, int omittedLoadedMessages,
        boolean historyWindowLimited, int estimatedInputTokens, int inputBudgetTokens,
        int reservedOutputTokens, int safetyTokens, String estimator, List<String> warnings) { }
