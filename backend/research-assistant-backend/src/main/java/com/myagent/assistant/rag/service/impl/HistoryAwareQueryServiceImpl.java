package com.myagent.assistant.rag.service.impl;

import com.myagent.assistant.chat.entity.ChatMessage;
import com.myagent.assistant.chat.service.ChatHistoryService;
import com.myagent.assistant.llm.LlmService;
import com.myagent.assistant.rag.dto.HistoryAwareQuery;
import com.myagent.assistant.rag.service.HistoryAwareQueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 最近三轮对话的轻量历史感知改写。
 */
@Service
public class HistoryAwareQueryServiceImpl implements HistoryAwareQueryService {

    private static final Logger log = LoggerFactory.getLogger(HistoryAwareQueryServiceImpl.class);
    private static final int MAX_HISTORY_MESSAGES = 6;
    private static final int MAX_MESSAGE_CHARS = 500;
    private static final int MAX_REWRITE_CHARS = 400;
    private static final Pattern EXPLICIT_PAPER = Pattern.compile(
            "(?i).*((论文|paper)\\s*[#：:]?\\s*\\d+|《[^》]{2,}》).*"
    );
    private static final Pattern CONTEXT_DEPENDENT = Pattern.compile(
            ".*(它|这个|这个方法|该方法|该论文|上述|前者|后者|二者|两者|相比|还有|继续|进一步|那么|这些|其|呢).*"
    );

    private final ChatHistoryService chatHistoryService;
    private final LlmService llmService;

    public HistoryAwareQueryServiceImpl(ChatHistoryService chatHistoryService, LlmService llmService) {
        this.chatHistoryService = chatHistoryService;
        this.llmService = llmService;
    }

    @Override
    public HistoryAwareQuery resolve(Long sessionId, String question) {
        if (question == null || question.isBlank()) {
            throw new RuntimeException("问题不能为空");
        }
        String original = question.trim();
        if (sessionId == null || EXPLICIT_PAPER.matcher(original).matches()) {
            return unchanged(original, 0);
        }

        List<ChatMessage> recentMessages;
        try {
            List<ChatMessage> allMessages = chatHistoryService.listMessages(sessionId);
            if (allMessages == null || allMessages.isEmpty()) {
                return unchanged(original, 0);
            }
            int fromIndex = Math.max(0, allMessages.size() - MAX_HISTORY_MESSAGES);
            recentMessages = allMessages.subList(fromIndex, allMessages.size());
        } catch (RuntimeException e) {
            log.warn("读取会话历史失败，回退原问题 sessionId={}: {}", sessionId, e.getMessage());
            return unchanged(original, 0);
        }

        return resolveWithHistory(sessionId, original, recentMessages);
    }

    @Override
    public HistoryAwareQuery resolveWithHistory(Long sessionId, String question, List<ChatMessage> messages) {
        if (question == null || question.isBlank()) throw new IllegalArgumentException("问题不能为空");
        String original = question.trim();
        if (sessionId == null || EXPLICIT_PAPER.matcher(original).matches() || messages == null || messages.isEmpty()) {
            return unchanged(original, 0);
        }
        List<ChatMessage> recentMessages = messages.subList(Math.max(0, messages.size() - MAX_HISTORY_MESSAGES), messages.size());
        if (!needsHistory(original)) {
            return unchanged(original, recentMessages.size());
        }

        String prompt = buildRewritePrompt(recentMessages, original);
        try {
            String rewritten = cleanRewrite(llmService.generateAnswer(prompt));
            if (rewritten == null || rewritten.isBlank()) {
                return unchanged(original, recentMessages.size());
            }
            return new HistoryAwareQuery(original, rewritten, !rewritten.equals(original), recentMessages.size());
        } catch (RuntimeException e) {
            log.warn("历史感知问题改写失败，回退原问题 sessionId={}: {}", sessionId, e.getMessage());
            return unchanged(original, recentMessages.size());
        }
    }

    private boolean needsHistory(String question) {
        if (CONTEXT_DEPENDENT.matcher(question).matches()) {
            return true;
        }
        return question.length() <= 12 && !question.matches(".*[A-Za-z]{3,}.*");
    }

    private String buildRewritePrompt(List<ChatMessage> messages, String question) {
        StringBuilder history = new StringBuilder();
        for (ChatMessage message : messages) {
            if (message == null || message.getContent() == null || message.getContent().isBlank()) {
                continue;
            }
            String role = "assistant".equalsIgnoreCase(message.getRole()) ? "助手" : "用户";
            String content = message.getContent().replaceAll("\\s+", " ").trim();
            if (content.length() > MAX_MESSAGE_CHARS) {
                content = content.substring(0, MAX_MESSAGE_CHARS);
            }
            history.append(role).append("：").append(content).append("\n");
        }

        return """
                你是论文问答系统的多轮检索问题改写器。

                请结合最近对话，把当前追问改写为一条不依赖上下文、可以直接检索论文的完整问题。

                要求：
                1. 解析“它、该方法、上述、前者、后者、相比”等指代。
                2. 保留对话中已经明确的论文 ID、论文名称、方法名和比较对象。
                3. 不添加对话中不存在的事实或研究对象。
                4. 只输出改写后的问题，不解释，不使用 JSON。
                5. 输出不超过 400 个字符。

                最近对话：
                %s
                当前追问：
                %s
                """.formatted(history, question);
    }

    private String cleanRewrite(String value) {
        if (value == null) {
            return null;
        }
        String result = value.replace("```", "").replaceAll("\\s+", " ").trim();
        result = result.replaceFirst("^(改写后的问题|独立问题|检索问题)[：:]\\s*", "");
        if (result.length() > MAX_REWRITE_CHARS) {
            return result.substring(0, MAX_REWRITE_CHARS);
        }
        return result;
    }

    private HistoryAwareQuery unchanged(String question, int historyMessageCount) {
        return new HistoryAwareQuery(question, question, false, historyMessageCount);
    }
}
