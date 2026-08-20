package com.myagent.assistant.writing.dto;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class WritingCitationAuditResponse {
    private Integer selectedPaperCount = 0;
    private Integer citedPaperCount = 0;
    private Integer validCitationCount = 0;
    private Integer invalidCitationCount = 0;
    private Integer factualParagraphCount = 0;
    private Integer citedParagraphCount = 0;
    private Double paragraphCoverage = 0.0;
    private List<Long> uncitedPaperIds = new ArrayList<>();
    private List<Long> invalidPaperIds = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();
}
