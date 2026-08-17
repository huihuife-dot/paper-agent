package com.myagent.assistant.researchengineering.dto;

import java.util.List;

public record AgentGitProjectResponse(
        String projectId,
        String mode,
        Long sourceId,
        String workspaceName,
        String status,
        String baselineCommit,
        String agentBranch,
        String latestCommit,
        String remoteProvider,
        String remoteUrl,
        String remoteStatus,
        List<String> remoteBranches,
        String taskPackageVersion,
        String message,
        String updatedAt,
        String deliveryDownloadUrl
) { }
