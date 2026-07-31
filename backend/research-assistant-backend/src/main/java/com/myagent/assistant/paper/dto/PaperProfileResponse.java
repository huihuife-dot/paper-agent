package com.myagent.assistant.paper.dto;

import lombok.Data;

/**
 * 文献画像接口响应。
 */
@Data
public class PaperProfileResponse {
    private Long id;
    private Long paperId;
    private String title;
    private String researchProblem;
    private String methodSummary;
    private String experimentSummary;
    private String keyContributions;
    private String limitations;
    private String keywords;
    private String profileText;
    private String profileVersion;
}
