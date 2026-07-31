package com.myagent.assistant.researchengineering.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import com.myagent.assistant.paper.reproduction.ReproductionSpecDocument;

import java.util.List;

/** Read-only online context for reproducing one selected paper. */
@Data
public class PaperReproductionContextResponse {
    private String sourceRevision;
    private AgentPaperResponse paper;
    private List<AgentEvidenceResponse> evidence;
    private AgentTaskPackageResponse taskPackage;
    private String protocolVersion;
    private ReproductionSpecDocument reproductionSpec;

    public PaperReproductionContextResponse(String sourceRevision, AgentPaperResponse paper,
                                            List<AgentEvidenceResponse> evidence,
                                            AgentTaskPackageResponse taskPackage) {
        this(sourceRevision, paper, evidence, taskPackage, "agent-context-v1", null);
    }

    public PaperReproductionContextResponse(String sourceRevision, AgentPaperResponse paper,
                                            List<AgentEvidenceResponse> evidence,
                                            AgentTaskPackageResponse taskPackage,
                                            String protocolVersion,
                                            ReproductionSpecDocument reproductionSpec) {
        this.sourceRevision = sourceRevision;
        this.paper = paper;
        this.evidence = evidence;
        this.taskPackage = taskPackage;
        this.protocolVersion = protocolVersion;
        this.reproductionSpec = reproductionSpec;
    }

    @Data
    @AllArgsConstructor
    public static class AgentPaperResponse {
        private Long paperId;
        private String title;
        private String abstractText;
        private String fullTextReference;
        private List<String> authorCodeUrls;
        private String updatedAt;
    }
}
