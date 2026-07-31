package com.myagent.assistant.idea.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Data;

import java.util.List;

/**
 * Research Idea 草稿响应。
 *
 * 注意：
 * 这个对象只用于“保存前预览”，不会直接写入 research_idea 表。
 * 用户确认后，前端再调用 POST /api/research-ideas 保存。
 */
@Data
public class ResearchIdeaDraftResponse {

    /**
     * 草稿标题。
     */
    private String title;

    /**
     * 原始内容摘要。
     *
     * 用于说明这个 idea 来自哪段对话。
     */
    private String originalContent;

    /**
     * 大模型整理后的研究想法正文。
     */
    private String refinedContent;

    /**
     * 可能的创新点。
     *
     * 大模型可能返回 JSON 数组或逗号分隔字符串，使用 FlexibleStringDeserializer 统一处理。
     */
    @JsonDeserialize(using = FlexibleStringDeserializer.class)
    private String innovationPoints;

    /**
     * 研究问题。
     */
    private String researchQuestion;

    /**
     * 可能的研究方法。
     *
     * 大模型可能返回 JSON 数组或逗号分隔字符串，使用 FlexibleStringDeserializer 统一处理。
     */
    @JsonDeserialize(using = FlexibleStringDeserializer.class)
    private String possibleMethod;

    /**
     * 标签。
     *
     * 先沿用当前 research_idea.tags 的设计，用逗号分隔字符串。
     * 大模型可能返回 JSON 数组，使用 FlexibleStringDeserializer 兜底。
     */
    @JsonDeserialize(using = FlexibleStringDeserializer.class)
    private String tags;

    /**
     * 来源类型。
     *
     * 当前固定为 rag_chat。
     */
    private String sourceType;

    /**
     * 来源会话 ID。
     */
    private Long sourceSessionId;

    /**
     * 本次草稿参考到的消息 ID。
     */
    private List<Long> sourceMessageIds;

    /**
     * 关联文献 ID。
     *
     * 当前阶段先保留为字符串，格式与 research_idea.relatedPaperIds 一致。
     * 大模型可能返回 JSON 数组，使用 FlexibleStringDeserializer 兜底。
     */
    @JsonDeserialize(using = FlexibleStringDeserializer.class)
    private String relatedPaperIds;

    /**
     * 大模型原始返回文本。
     *
     * 用于调试：如果 JSON 解析失败，也方便查看模型到底返回了什么。
     */
    private String rawLlmOutput;
}