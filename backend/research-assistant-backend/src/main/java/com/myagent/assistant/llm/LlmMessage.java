package com.myagent.assistant.llm;

import java.util.List;

/** 一条真正的模型消息。历史保留角色，不拼成一条冒充用户的新问题。 */
public record LlmMessage(String role, String content) {
    public LlmMessage {
        if (role == null || !List.of("system", "user", "assistant").contains(role)) {
            throw new IllegalArgumentException("不支持的消息角色");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("消息内容不能为空");
        }
    }
}
