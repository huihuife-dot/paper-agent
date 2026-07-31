package com.myagent.assistant.paper.reproduction;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReproductionSpecDocument {
    private String schemaVersion;
    private Long paperId;
    private String sourceRevision;
    private String status;
    private double criticalCoverage;
    private double provenanceCoverage;
    private List<FactItem> trustedFacts;
    private List<FactItem> reviewRequiredFacts;
    private List<FactItem> modelInferredFacts;
    private List<ConflictItem> conflicts;
    private List<String> missingInformation;
    private List<String> safeDefaults;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FactItem {
        private Long factId;
        private String factType;
        private String key;
        private String value;
        private String unit;
        private String conditionsJson;
        private String sourceKind;
        private Long sourceId;
        private Integer pageNumber;
        private String evidenceExcerpt;
        private double confidence;
        private String verificationStatus;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConflictItem {
        private String conflictGroup;
        private String factKey;
        private List<FactItem> candidates;
    }
}
