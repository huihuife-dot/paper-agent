package com.myagent.assistant.researchengineering.git;

public record GitCommandResult(int exitCode, String output) {
    public boolean successful() { return exitCode == 0; }
}
