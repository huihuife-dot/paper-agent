package com.myagent.assistant.rag.dto;

import lombok.Data;

import java.util.List;

/**
 * RAG 问答请求。
 */
@Data
public class RagChatRequest {

    /**
     * 用户问题。
     */
    private String question;

    /**
     * 检索返回的来源数量。
     * 如果前端不传，后端默认使用 5。
     */
    private Integer topK;

    /**
     * 对话会话 ID。
     *
     * 如果为空，后端会自动创建新会话；
     * 如果不为空，则把本轮问答追加到已有会话。
     */
    private Long sessionId;

    /**
     * 关联文献 ID。
     *
     * 当前可为空，用于兼容旧的单篇文献问答请求。
     */
    private Long paperId;

    /**
     * 限定检索的文献 ID 列表。
     *
     * 为空或空列表时，默认检索全部文献；
     * 不为空时，只从这些文献的 chunks 中检索。
     */
    private List<Long> paperIds;
}