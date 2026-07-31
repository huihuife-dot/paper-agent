package com.myagent.assistant.researchengineering.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import com.myagent.assistant.paper.reproduction.ReproductionSpecDocument;

import java.util.List;

/**
 * A stable, implementation-oriented brief for the local Research Engineering
 * Agent. It is deliberately read-only: local repository paths and credentials
 * are added only on the user's computer by the localhost Bridge.
 */
@Data
public class AgentTaskPackageResponse {
    private String taskType;
    private String goal;
    private List<String> implementationSteps;
    private List<InterfaceItem> interfaces;
    private List<ParameterItem> parameters;
    private List<AgentEvidenceResponse> evidence;
    private List<String> assumptionsAndGaps;
    private CodeScope codeScope;
    private List<String> acceptanceChecks;
    private String protocolVersion;
    private ReproductionSpecDocument reproductionSpec;
    private List<ReproductionSpecDocument.ConflictItem> conflicts;
    private List<String> missingInformation;
    private List<String> safeDefaults;

    public AgentTaskPackageResponse(String taskType, String goal, List<String> implementationSteps,
                                    List<InterfaceItem> interfaces, List<ParameterItem> parameters,
                                    List<AgentEvidenceResponse> evidence, List<String> assumptionsAndGaps,
                                    CodeScope codeScope, List<String> acceptanceChecks) {
        this(taskType, goal, implementationSteps, interfaces, parameters, evidence, assumptionsAndGaps,
                codeScope, acceptanceChecks, "agent-task-package-v1", null, List.of(), List.of(), List.of());
    }

    public AgentTaskPackageResponse(String taskType, String goal, List<String> implementationSteps,
                                    List<InterfaceItem> interfaces, List<ParameterItem> parameters,
                                    List<AgentEvidenceResponse> evidence, List<String> assumptionsAndGaps,
                                    CodeScope codeScope, List<String> acceptanceChecks, String protocolVersion,
                                    ReproductionSpecDocument reproductionSpec,
                                    List<ReproductionSpecDocument.ConflictItem> conflicts,
                                    List<String> missingInformation, List<String> safeDefaults) {
        this.taskType = taskType;
        this.goal = goal;
        this.implementationSteps = implementationSteps;
        this.interfaces = interfaces;
        this.parameters = parameters;
        this.evidence = evidence;
        this.assumptionsAndGaps = assumptionsAndGaps;
        this.codeScope = codeScope;
        this.acceptanceChecks = acceptanceChecks;
        this.protocolVersion = protocolVersion;
        this.reproductionSpec = reproductionSpec;
        this.conflicts = conflicts;
        this.missingInformation = missingInformation;
        this.safeDefaults = safeDefaults;
    }

    @Data
    @AllArgsConstructor
    public static class InterfaceItem {
        private String name;
        private String description;
    }

    @Data
    @AllArgsConstructor
    public static class ParameterItem {
        private String name;
        private String value;
        private String source;
    }

    @Data
    @AllArgsConstructor
    public static class CodeScope {
        private List<String> allowedPaths;
        private List<String> prohibitedPaths;
        private String note;
    }
}
