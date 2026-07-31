package com.myagent.assistant.rag.dto;

import lombok.Data;

/**
 * RAG 来源片段。
 *
 * 表示一次向量检索命中的一个 chunk。
 * 后续大模型回答时，会把这些 source 拼进 prompt。
 */
@Data
public class RagSource {

    /**
     * 文献 ID。
     */
    private Long paperId;

    /**
     * 文献标题。
     */
    private String paperTitle;

    /**
     * 上下文来源类型。
     *
     * 可选值：paper_profile、section_summary、raw_chunk、full_text。
     */
    private String sourceType;

    /**
     * chunk ID。
     */
    private Long chunkId;

    /**
     * chunk 在文献中的序号。
     */
    private Integer chunkIndex;

    /**
     * 章节摘要 ID。
     */
    private Long sectionSummaryId;

    /**
     * 章节摘要版本。
     */
    private String summaryVersion;

    /**
     * 文献画像 ID。
     */
    private Long profileId;

    /**
     * 文献画像版本。
     */
    private String profileVersion;

    /**
     * 所属章节 ID。
     */
    private Long sectionId;

    /**
     * 章节标题。
     */
    private String sectionTitle;

    /**
     * 标准章节类型。
     */
    private String sectionType;

    /**
     * 是否参考文献片段。
     */
    private Boolean isReference;

    /**
     * 是否噪声片段。
     */
    private Boolean isNoise;

    /**
     * 分块策略版本。
     */
    private String chunkStrategyVersion;

    /**
     * Qdrant 相似度分数。
     */
    private Double score;

    /**
     * chunk 完整原文。
     *
     * 注意：这里应该来自 MySQL 的 paper_chunk.content，
     * 而不是 Qdrant payload 中的 text 预览。
     */
    private String content;


    /**
     * 召回来源。
     *
     * original 表示用户原始问题召回；
     * rewritten 表示英文改写问题召回。
     */
    private String retrievalRoute;
}