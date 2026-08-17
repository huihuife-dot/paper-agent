package com.myagent.assistant.researchengineering.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 单机部署时由 Spring Boot 托管 Agent 进程的最小配置。
 * 命令和目录只允许由服务器配置提供，不能由网页请求覆盖。
 */
@Component
@ConfigurationProperties(prefix = "app.agent-execution")
public class AgentExecutionProperties {
    private boolean enabled = true;
    private String command = "research-engineering";
    private String workspaceRoot = "./ResearchAssistantData/agent-workspaces";
    private String ideaWorkspaceRoot = "./ResearchAssistantData/idea-workspaces";
    private String serverUrl = "http://127.0.0.1:8080";
    private String envFile = "";
    private int outputLines = 80;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getCommand() { return command; }
    public void setCommand(String command) { this.command = command; }
    public String getWorkspaceRoot() { return workspaceRoot; }
    public void setWorkspaceRoot(String workspaceRoot) { this.workspaceRoot = workspaceRoot; }
    public String getIdeaWorkspaceRoot() { return ideaWorkspaceRoot; }
    public void setIdeaWorkspaceRoot(String ideaWorkspaceRoot) { this.ideaWorkspaceRoot = ideaWorkspaceRoot; }
    public String getServerUrl() { return serverUrl; }
    public void setServerUrl(String serverUrl) { this.serverUrl = serverUrl; }
    public String getEnvFile() { return envFile; }
    public void setEnvFile(String envFile) { this.envFile = envFile; }
    public int getOutputLines() { return outputLines; }
    public void setOutputLines(int outputLines) { this.outputLines = outputLines; }
}
