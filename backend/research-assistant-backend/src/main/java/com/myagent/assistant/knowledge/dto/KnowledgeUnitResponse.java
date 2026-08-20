package com.myagent.assistant.knowledge.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class KnowledgeUnitResponse {
    private Long id;
    private Long paperId;
    private String paperTitle;
    private Long sectionId;
    private String sectionTitle;
    private String sectionType;
    private Long chunkId;
    private String knowledgeType;
    private String subjectText;
    private String predicateText;
    private String objectValue;
    private String valueUnit;
    private String applicableCondition;
    private Boolean hasConflict;
    private String sourceType;
    private Long sourceId;
    private Integer pageNumber;
    private String evidenceText;
    private String confidenceLevel;
    private String verificationStatus;
    private String extractionMethod;
    private String extractionVersion;
    private LocalDateTime updateTime;
}
