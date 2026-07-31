package com.myagent.assistant.chat.controller;

import com.myagent.assistant.chat.entity.ChatMessage;
import com.myagent.assistant.chat.entity.ChatSession;
import com.myagent.assistant.chat.service.ChatHistoryService;
import com.myagent.assistant.paper.common.Result;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 对话历史控制器。
 */
@RestController
public class ChatHistoryController {

    private final ChatHistoryService chatHistoryService;

    public ChatHistoryController(ChatHistoryService chatHistoryService) {
        this.chatHistoryService = chatHistoryService;
    }

    /**
     * 创建新对话会话。
     *
     * 示例：
     * POST /api/chat/sessions?title=论文问答&paperId=1
     */
    @PostMapping("/api/chat/sessions")
    public Result<ChatSession> createSession(
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "paperId", required = false) Long paperId
    ) {
        return Result.success(chatHistoryService.createSession(title, paperId));
    }

    /**
     * 查询会话列表。
     *
     * 示例：
     * GET /api/chat/sessions
     */
    @GetMapping("/api/chat/sessions")
    public Result<List<ChatSession>> listSessions() {
        return Result.success(chatHistoryService.listSessions());
    }

    /**
     * 查询某个会话下的消息列表。
     *
     * 示例：
     * GET /api/chat/sessions/1/messages
     */
    @GetMapping("/api/chat/sessions/{sessionId}/messages")
    public Result<List<ChatMessage>> listMessages(@PathVariable Long sessionId) {
        return Result.success(chatHistoryService.listMessages(sessionId));
    }

    /**
     * 删除会话及其消息。
     *
     * 示例：
     * DELETE /api/chat/sessions/1
     */
    @DeleteMapping("/api/chat/sessions/{sessionId}")
    public Result<Void> deleteSession(@PathVariable Long sessionId) {
        chatHistoryService.deleteSession(sessionId);
        return Result.success(null);
    }
}