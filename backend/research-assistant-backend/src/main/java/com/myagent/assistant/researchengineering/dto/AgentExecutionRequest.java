package com.myagent.assistant.researchengineering.dto;

/** request is Agent-facing natural language, never a shell command. */
public record AgentExecutionRequest(String request, String workspacePath) { }
