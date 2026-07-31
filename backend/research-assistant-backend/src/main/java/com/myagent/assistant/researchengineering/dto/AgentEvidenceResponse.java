package com.myagent.assistant.researchengineering.dto;

import lombok.Data;

/**
 * A small, traceable piece of information sent to the local code agent.
 * It intentionally contains no server file path or credential.
 */
@Data
public class AgentEvidenceResponse {
    private String id;
    private String kind;
    private String title;
    private String content;
    private String sourceReference;
    private Long paperId;
    private String sourceKind;
    private Long sourceId;
    private Integer pageNumber;
    private String verificationStatus;
    private Double confidence;

    /** v1-compatible constructor retained for old Idea and summary evidence. */
    public AgentEvidenceResponse(String id, String kind, String title, String content, String sourceReference) {
        this(id, kind, title, content, sourceReference, null, null, null, null, null, null);
    }

    public AgentEvidenceResponse(String id, String kind, String title, String content, String sourceReference,
                                 Long paperId, String sourceKind, Long sourceId, Integer pageNumber,
                                 String verificationStatus, Double confidence) {
        this.id = id;
        this.kind = kind;
        this.title = title;
        this.content = content;
        this.sourceReference = sourceReference;
        this.paperId = paperId;
        this.sourceKind = sourceKind;
        this.sourceId = sourceId;
        this.pageNumber = pageNumber;
        this.verificationStatus = verificationStatus;
        this.confidence = confidence;
    }
}
