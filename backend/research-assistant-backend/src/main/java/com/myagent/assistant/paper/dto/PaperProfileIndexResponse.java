package com.myagent.assistant.paper.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class PaperProfileIndexResponse {
    private Long paperId;
    private Boolean success;
    private Boolean profileIndexed;
    private Integer totalSectionSummaries;
    private Integer indexedSectionSummaries;
    private List<String> failures = new ArrayList<>();
}
