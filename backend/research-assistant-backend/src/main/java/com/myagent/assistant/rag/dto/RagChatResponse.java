package com.myagent.assistant.rag.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * RAG 问答响应。
 */
@Data
public class RagChatResponse {


    /**
     * 本次 RAG 问答构造出来的 prompt。
     *
     * 当前阶段用于调试；
     * 后续正式接入大模型后，可以根据需要决定是否返回给前端。
     */
    private String prompt;


    /**
     * 用户问题。
     */
    private String question;

    /**
     * AI 回答。
     *
     * 当前阶段先返回占位内容；
     * 后续接入真实大模型后，这里会变成模型生成的回答。
     */
    private String answer;

    /**
     * 本次回答参考的文献来源片段。
     */
    private List<RagSource> sources;

    /**
     * 本次使用的大模型供应商。
     */
    private String modelProvider;

    /**
     * 本次使用的大模型名称。
     */
    private String modelName;

    /**
     * 本次返回的来源片段数量。
     */
    private Integer sourceCount;

    /**
     * 本次问答所属会话 ID。
     */
    private Long sessionId;

    /**
     * 实际用于向量检索的问题。
     *
     * 中文问题可能会被翻译成英文 query 后再检索英文 chunk。
     */
    private String retrievalQuestion;

    /**
     * 是否建议用户将本次 RAG 回答保留为 Research Idea。
     *
     * 当前阶段由后端规则判断；
     * 后续可升级为大模型判断。
     */
    private Boolean suggestSaveAsIdea;

    /**
     * 建议保留为 Research Idea 的原因。
     */
    private String ideaSuggestionReason;

    /**
     * 本轮使用的上下文策略：VECTOR_RAG / FULL_TEXT_PARSED / HYBRID_RAG / LIBRARY_DISCOVERY。
     */
    private String contextStrategy;

    /**
     * 本轮上下文估算 token 数。
     */
    private Integer contextTokenCount;

    /**
     * 本轮上下文实际使用的论文 ID。
     */
    private List<Long> contextPaperIds;

    /**
     * 结构化优先取证的可解释计划；旧链路回退时可以为空。
     */
    private EvidenceQueryPlan evidencePlan;

    /**
     * 全库检索时按论文聚合的相关度列表。
     *
     * 仅在 contextStrategy=LIBRARY_DISCOVERY 时非空。
     */
    private List<PaperRelevance> paperRelevance = new ArrayList<>();

    /**
     * 本轮 RAG 分阶段耗时，仅用于性能诊断。
     */
    private RagTiming timing;
}
