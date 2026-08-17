package com.myagent.assistant.researchengineering.dto;

public record AgentPackageResponse(
        String packageId,
        String taskType,
        Long sourceId,
        String sourceRevision,
        String packageVersion,
        String fileName,
        String downloadUrl,
        String createdAt
) { }
