package com.myagent.assistant.paper.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文献画像异步任务状态响应。
 */
@Data
public class PaperProfileJobResponse {
    private Long id;
    private Long paperId;
    private String status;
    private String currentStep;
    private Integer progressPercent;
    private Integer processedSections;
    private Integer totalSections;
    private Integer retryCount;
    private String errorMessage;
    private LocalDateTime startTime;
    private LocalDateTime finishTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
