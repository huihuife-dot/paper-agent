package com.myagent.assistant.knowledge.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class KnowledgeCatalogResponse {
    private Long paperId;
    private String paperTitle;
    private Integer publishYear;
    private String status;
    private List<String> researchDomains = new ArrayList<>();
    private List<String> researchTasks = new ArrayList<>();
    private List<String> methodTags = new ArrayList<>();
    private List<String> datasetTags = new ArrayList<>();
    private List<String> metricTags = new ArrayList<>();
    private Integer knowledgeCount;
    private Integer verifiedCount;
    private String extractionVersion;
    private String modelProvider;
    private String modelName;
    private String errorMessage;
    private LocalDateTime buildTime;
}
