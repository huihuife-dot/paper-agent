package com.myagent.assistant.writing.dto;

import lombok.Data;

@Data
public class WritingPaperResponse {
    private Long id;
    private String title;
    private String authors;
    private Integer publishYear;
    private String journal;
}
