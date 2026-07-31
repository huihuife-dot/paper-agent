package com.myagent.assistant.paper.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PaperAssetAnalysisUpdateRequest {
    @Size(max = 200000) private String structuredContentJson;
    @Size(max = 200000) private String semanticDescription;
}
