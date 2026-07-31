package com.myagent.assistant.researchengineering.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/** Read-only online context for improving an existing local repository from one Idea. */
@Data
@AllArgsConstructor
public class IdeaImprovementContextResponse {
    private String sourceRevision;
    private AgentIdeaResponse idea;
    private List<AgentEvidenceResponse> evidence;
    private AgentTaskPackageResponse taskPackage;

    @Data
    @AllArgsConstructor
    public static class AgentIdeaResponse {
        private Long ideaId;
        private String title;
        private String originalContent;
        private String refinedContent;
        private String researchQuestion;
        private String possibleMethod;
        private String innovationPoints;
        private List<String> tags;
        private Long sourceSessionId;
        private Long sourceMessageId;
        private String updatedAt;
    }
}
