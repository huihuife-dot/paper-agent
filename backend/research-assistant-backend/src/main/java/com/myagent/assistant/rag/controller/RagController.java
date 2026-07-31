package com.myagent.assistant.rag.controller;

import com.myagent.assistant.paper.common.Result;
import com.myagent.assistant.rag.dto.RagSource;
import com.myagent.assistant.rag.service.RagRetrievalService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.myagent.assistant.rag.dto.RagChatRequest;
import com.myagent.assistant.rag.dto.RagChatResponse;
import com.myagent.assistant.rag.service.RagChatService;
import com.myagent.assistant.rag.service.RagStreamListener;
import com.myagent.assistant.rag.dto.RagStreamMetadata;
import org.springframework.core.task.TaskExecutor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * RAG 控制器。
 *
 * 当前阶段先提供 sources 调试接口，
 * 用于验证“问题 -> Qdrant 检索 -> MySQL 回查 chunk 原文”的链路。
 */
@RestController
public class RagController {

    private final RagChatService ragChatService;

    private final RagRetrievalService ragRetrievalService;

    private final TaskExecutor taskExecutor;

    public RagController(RagRetrievalService ragRetrievalService,
                         RagChatService ragChatService,
                         @Qualifier("ragStreamTaskExecutor") TaskExecutor taskExecutor) {
        this.ragRetrievalService = ragRetrievalService;
        this.ragChatService = ragChatService;
        this.taskExecutor = taskExecutor;
    }

    /**
     * 检索与问题相关的文献 chunk 来源。
     *
     * 访问示例：
     * GET /api/rag/sources?question=transformer&topK=5
     */
    @GetMapping("/api/rag/sources")
    public Result<List<RagSource>> sources(
            @RequestParam("question") String question,
            @RequestParam(value = "topK", required = false) Integer topK,
            @RequestParam(value = "paperIds", required = false) String paperIds
    ) {
        return Result.success(ragRetrievalService.retrieveSources(question, topK, parsePaperIds(paperIds)));
    }




    /**
     * 最小 RAG 问答接口。
     *
     * 访问示例：
     * POST /api/rag/chat
     *
     * 请求体：
     * {
     *   "question": "这篇论文主要讲了什么？",
     *   "topK": 5
     * }
     *
     * 当前阶段暂不调用真实大模型，
     * 先返回占位回答和检索到的 sources。
     */
    @PostMapping("/api/rag/chat")
    public Result<RagChatResponse> chat(@RequestBody RagChatRequest request) {
        return Result.success(ragChatService.chat(request));
    }

    /**
     * 真正的 SSE 流式 RAG 接口。检索完成后发送 metadata，模型生成过程中发送 delta，
     * 最后发送与同步接口结构一致的 complete 数据。
     */
    @PostMapping(value = "/api/rag/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@RequestBody RagChatRequest request) {
        SseEmitter emitter = new SseEmitter(180_000L);
        sendEvent(emitter, "phase", Map.of("phase", "retrieving"));

        taskExecutor.execute(() -> {
            try {
                RagChatResponse response = ragChatService.chatStream(request, new RagStreamListener() {
                    @Override
                    public void onMetadata(RagStreamMetadata metadata) {
                        sendEvent(emitter, "metadata", metadata);
                    }

                    @Override
                    public void onDelta(String content) {
                        sendEvent(emitter, "delta", Map.of("content", content));
                    }
                });
                sendEvent(emitter, "complete", response);
                emitter.complete();
            } catch (Exception e) {
                try {
                    sendEvent(emitter, "error", Map.of("message", rootMessage(e)));
                    emitter.complete();
                } catch (Exception ignored) {
                    emitter.completeWithError(e);
                }
            }
        });
        return emitter;
    }

    private void sendEvent(SseEmitter emitter, String name, Object data) {
        try {
            emitter.send(SseEmitter.event()
                    .name(name)
                    .data(data, MediaType.APPLICATION_JSON));
        } catch (Exception e) {
            throw new RuntimeException("SSE 连接已中断", e);
        }
    }

    private String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? "流式回答生成失败" : current.getMessage();
    }


    /**
     * 解析调试接口中的文献范围参数。
     *
     * Apifox 中可传：paperIds=1,2,3。
     */
    private List<Long> parsePaperIds(String paperIds) {
        if (paperIds == null || paperIds.isBlank()) {
            return List.of();
        }

        return Arrays.stream(paperIds.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(Long::valueOf)
                .filter(id -> id > 0)
                .distinct()
                .toList();
    }


}
