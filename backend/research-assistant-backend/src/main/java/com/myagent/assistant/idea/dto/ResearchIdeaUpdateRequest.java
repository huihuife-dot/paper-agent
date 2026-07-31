package com.myagent.assistant.idea.dto;

import lombok.Data;

/**
 * 更新 Research Idea 请求。
 *
 * 当前采用全量覆盖方式：
 * 前端传什么字段，后端就更新什么字段。
 */
@Data
public class ResearchIdeaUpdateRequest {

    private String title;

    private String originalContent;

    private String refinedContent;

    private String innovationPoints;

    private String researchQuestion;

    private String possibleMethod;

    private String tags;

    private String sourceType;

    private String saveType;

    private Long sourceSessionId;

    private Long sourceMessageId;

    private String relatedPaperIds;
}