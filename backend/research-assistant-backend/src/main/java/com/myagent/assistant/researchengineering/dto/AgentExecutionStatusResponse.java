package com.myagent.assistant.researchengineering.dto;

import java.util.List;

public record AgentExecutionStatusResponse(
        String mode,
        Long sourceId,
        String status,
        String phase,
        String workspaceName,
        String message,
        List<String> recentOutput,
        boolean stoppable
) { }
