package com.myagent.assistant.researchengineering.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Configuration for portable packages, local Git and optional Gitee publishing. */
@Component
@ConfigurationProperties(prefix = "app.agent-delivery")
public class AgentDeliveryProperties {
    private String packageRoot = "./ResearchAssistantData/agent-packages";
    private String projectIndexFile = "./ResearchAssistantData/agent-git-projects.json";
    private String gitCommand = "git";
    private String gitUserName = "MyAgent";
    private String gitUserEmail = "myagent@localhost";
    private boolean giteeEnabled;
    private String giteeBaseUrl = "https://gitee.com/api/v5";
    private String giteeToken = "";
    private String giteeOwner = "";
    private String giteeRemoteName = "gitee";

    public String getPackageRoot() { return packageRoot; }
    public void setPackageRoot(String packageRoot) { this.packageRoot = packageRoot; }
    public String getGitCommand() { return gitCommand; }
    public void setGitCommand(String gitCommand) { this.gitCommand = gitCommand; }
    public String getProjectIndexFile() { return projectIndexFile; }
    public void setProjectIndexFile(String projectIndexFile) { this.projectIndexFile = projectIndexFile; }
    public String getGitUserName() { return gitUserName; }
    public void setGitUserName(String gitUserName) { this.gitUserName = gitUserName; }
    public String getGitUserEmail() { return gitUserEmail; }
    public void setGitUserEmail(String gitUserEmail) { this.gitUserEmail = gitUserEmail; }
    public boolean isGiteeEnabled() { return giteeEnabled; }
    public void setGiteeEnabled(boolean giteeEnabled) { this.giteeEnabled = giteeEnabled; }
    public String getGiteeBaseUrl() { return giteeBaseUrl; }
    public void setGiteeBaseUrl(String giteeBaseUrl) { this.giteeBaseUrl = giteeBaseUrl; }
    public String getGiteeToken() { return giteeToken; }
    public void setGiteeToken(String giteeToken) { this.giteeToken = giteeToken; }
    public String getGiteeOwner() { return giteeOwner; }
    public void setGiteeOwner(String giteeOwner) { this.giteeOwner = giteeOwner; }
    public String getGiteeRemoteName() { return giteeRemoteName; }
    public void setGiteeRemoteName(String giteeRemoteName) { this.giteeRemoteName = giteeRemoteName; }
}
