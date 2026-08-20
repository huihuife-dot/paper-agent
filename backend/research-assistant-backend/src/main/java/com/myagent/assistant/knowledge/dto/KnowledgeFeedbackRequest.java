package com.myagent.assistant.knowledge.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class KnowledgeFeedbackRequest {
    @NotBlank
    private String action;
    private String subjectText;
    private String predicateText;
    private String objectValue;
    private String valueUnit;
    private String applicableCondition;
    private String comment;
}
