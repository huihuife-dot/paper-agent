package com.myagent.assistant.researchengineering.git;

import lombok.Data;

import java.util.List;

@Data
public class AgentGitProjectDocument {
    private String projectId;
    private String mode;
    private Long sourceId;
    private String workspace;
    private String status;
    private String baselineCommit;
    private String agentBranch;
    private String latestCommit;
    private String remoteProvider;
    private String remoteName;
    private String remoteUrl;
    private String remoteSshUrl;
    private String remoteStatus;
    private List<String> remoteBranches;
    private String taskPackageVersion;
    private String updatedAt;
    private String message;
}
