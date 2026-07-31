package com.myagent.assistant.paper.dto;

import lombok.Data;

/**
 * 章节摘要接口响应。
 */
@Data
public class PaperSectionSummaryResponse {
    private Long id;
    private Long paperId;
    private Long sectionId;
    private String sectionType;
    private String sectionTitle;
    private String summary;
    private String keyPoints;
    private Integer sourceTokenCount;
    private String summaryVersion;
}
