package com.myagent.assistant.idea.dto;

import lombok.Data;

/**
 * 创建 Research Idea 请求。
 */
@Data
public class ResearchIdeaCreateRequest {

    private String title;

    private String originalContent;

    private String refinedContent;

    private String innovationPoints;

    private String researchQuestion;

    private String possibleMethod;

    private String tags;

    /**
     * 来源类型。
     * 不传时默认 manual。
     */
    private String sourceType;

    /**
     * 保存类型。
     * 不传时默认 draft。
     */
    private String saveType;

    private Long sourceSessionId;

    private Long sourceMessageId;

    private String relatedPaperIds;
}