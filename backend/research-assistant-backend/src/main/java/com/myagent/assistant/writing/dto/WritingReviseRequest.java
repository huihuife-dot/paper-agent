package com.myagent.assistant.writing.dto;

import lombok.Data;

@Data
public class WritingReviseRequest {
    private String instruction;
    private String selectedText;
}
