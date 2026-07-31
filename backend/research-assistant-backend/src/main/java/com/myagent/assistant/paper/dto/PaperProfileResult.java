package com.myagent.assistant.paper.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 文献画像聚合结果。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaperProfileResult {
    private PaperProfileResponse profile;
    private List<PaperSectionSummaryResponse> sectionSummaries;
}
