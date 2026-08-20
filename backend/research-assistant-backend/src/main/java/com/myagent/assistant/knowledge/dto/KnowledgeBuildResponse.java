package com.myagent.assistant.knowledge.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class KnowledgeBuildResponse {
    private Long paperId;
    private String status;
    private Integer knowledgeCount;
    private Integer verifiedCount;
    private Integer conflictCount;
    private String errorMessage;
    private String extractionVersion;
    private String modelProvider;
    private String modelName;
    private LocalDateTime buildTime;
}
