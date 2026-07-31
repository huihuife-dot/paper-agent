package com.myagent.assistant.paper.dto;

import lombok.Data;

/**
 * 文献页使用的轻量画像状态。
 */
@Data
public class PaperProfileStatusResponse {
    private Long paperId;
    private Boolean hasProfile;
    private String profileVersion;
    private Integer sectionSummaryCount;
    private Boolean profileIndexed;
    private Integer indexedSectionSummaryCount;
    private Boolean profileIndexComplete;
    private String jobStatus;
    private String currentStep;
    private Integer progressPercent;
    private Integer processedSections;
    private Integer totalSections;
    private Integer retryCount;
    private String errorMessage;
}
