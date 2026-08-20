package com.myagent.assistant.writing.dto;

import lombok.Data;
import java.util.List;

@Data
public class WritingProjectUpdateRequest {
    private String title;
    private String topic;
    private String documentType;
    private String targetLanguage;
    private Integer targetWordCount;
    private String citationStyle;
    private List<Long> paperIds;
    private String outlineJson;
    private String content;
}
