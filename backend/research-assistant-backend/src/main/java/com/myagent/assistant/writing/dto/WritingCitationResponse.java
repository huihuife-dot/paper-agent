package com.myagent.assistant.writing.dto;

import lombok.Data;

@Data
public class WritingCitationResponse {
    private String marker;
    private Long paperId;
    private String title;
    private String authors;
    private Integer publishYear;
    private String journal;
    private Long chunkId;
    private Integer pageNumber;
    private String sectionTitle;
    private String excerpt;
}
