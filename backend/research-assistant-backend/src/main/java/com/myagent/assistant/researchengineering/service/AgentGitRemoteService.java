package com.myagent.assistant.researchengineering.service;

import com.myagent.assistant.researchengineering.config.AgentDeliveryProperties;
import com.myagent.assistant.researchengineering.dto.AgentGitProjectResponse;
import com.myagent.assistant.researchengineering.dto.GiteePublishRequest;
import com.myagent.assistant.researchengineering.git.AgentGitProjectDocument;
import com.myagent.assistant.researchengineering.git.AgentGitProjectStore;
import com.myagent.assistant.researchengineering.git.GitCommandResult;
import com.myagent.assistant.researchengineering.git.GitCommandRunner;
import com.myagent.assistant.researchengineering.git.GiteeRepository;
import com.myagent.assistant.researchengineering.git.GiteeRepositoryClient;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Service
public class AgentGitRemoteService {
    private final GiteeRepositoryClient gitee;
    private final GitCommandRunner git;
    private final AgentGitProjectStore store;
    private final AgentGitService gitService;
    private final AgentDeliveryProperties properties;

    public AgentGitRemoteService(GiteeRepositoryClient gitee, GitCommandRunner git, AgentGitProjectStore store,
                                 AgentGitService gitService, AgentDeliveryProperties properties) {
        this.gitee = gitee;
        this.git = git;
        this.store = store;
        this.gitService = gitService;
        this.properties = properties;
    }

    public AgentGitProjectResponse publish(String mode, long sourceId, GiteePublishRequest request) {
        String name = request == null ? "" : String.valueOf(request.repositoryName()).trim();
        if (!name.matches("[A-Za-z0-9._-]{2,100}")) throw new IllegalArgumentException("Gitee 仓库名只能包含字母、数字、点、下划线和短横线");
        AgentGitProjectDocument project = required(mode, sourceId);
        if (project.getRemoteProvider() != null && !project.getRemoteProvider().isBlank()) {
            throw new IllegalStateException("该复现项目已经绑定远程仓库，请使用重试推送或同步功能");
        }
        Path workspace = Path.of(project.getWorkspace());
        String remote = properties.getGiteeRemoteName();
        GitCommandResult existingRemote = run(workspace, List.of("remote", "get-url", remote));
        if (existingRemote.successful()) {
            throw new IllegalStateException("本地 Git 已存在名为 " + remote + " 的远程仓库，请先人工确认，系统不会覆盖");
        }
        GiteeRepository repository = gitee.createPrivateRepository(name, request.description());
        runRequired(workspace, List.of("remote", "add", remote, repository.sshUrl()), "绑定 Gitee 远程仓库失败");
        try {
            runRequired(workspace, List.of("push", "-u", remote, "main"), "推送 Gitee main 分支失败，请检查服务器 SSH Key");
            runRequired(workspace, List.of("push", "-u", remote, project.getAgentBranch()), "推送 Gitee Agent 分支失败，请检查服务器 SSH Key");
            project.setRemoteStatus("PUSHED");
            project.setMessage("任务基线和 Agent 分支已推送到 Gitee");
        } catch (RuntimeException e) {
            project.setRemoteStatus("PUSH_PENDING");
            project.setMessage(e.getMessage());
        }
        project.setRemoteProvider("GITEE");
        project.setRemoteName(remote);
        project.setRemoteUrl(repository.htmlUrl());
        project.setRemoteSshUrl(repository.sshUrl());
        project.setUpdatedAt(LocalDateTime.now().toString());
        store.save(project);
        return gitService.get(mode, sourceId);
    }

    public AgentGitProjectResponse retryPush(String mode, long sourceId) {
        AgentGitProjectDocument project = required(mode, sourceId);
        if (!"GITEE".equals(project.getRemoteProvider())) throw new IllegalStateException("该项目尚未绑定 Gitee");
        Path workspace = Path.of(project.getWorkspace());
        runRequired(workspace, List.of("push", "-u", project.getRemoteName(), "main"), "重试推送 Gitee main 分支失败");
        runRequired(workspace, List.of("push", "-u", project.getRemoteName(), project.getAgentBranch()), "重试推送 Gitee Agent 分支失败");
        project.setRemoteStatus("PUSHED");
        project.setMessage("本地提交已重新推送到 Gitee");
        project.setUpdatedAt(LocalDateTime.now().toString());
        store.save(project);
        return gitService.get(mode, sourceId);
    }

    public AgentGitProjectResponse refresh(String mode, long sourceId) {
        AgentGitProjectDocument project = required(mode, sourceId);
        if (!"GITEE".equals(project.getRemoteProvider())) throw new IllegalStateException("该项目尚未绑定 Gitee");
        Path workspace = Path.of(project.getWorkspace());
        runRequired(workspace, List.of("fetch", "--prune", project.getRemoteName()), "刷新 Gitee 分支失败");
        GitCommandResult branches = runRequired(workspace,
                List.of("branch", "-r", "--format=%(refname:short)"), "读取远程分支失败");
        project.setRemoteBranches(Arrays.stream(branches.output().split("\\R")).map(String::trim).filter(value -> !value.isBlank()).toList());
        GitCommandResult dirty = runRequired(workspace, List.of("status", "--porcelain"), "检查本地工作区失败");
        if (!dirty.output().isBlank()) throw new IllegalStateException("本地工作区存在未提交修改，暂不能同步外部 Agent 代码");
        String trackedRemoteBranch = project.getRemoteName() + "/" + project.getAgentBranch();
        String before = project.getLatestCommit();
        runRequired(workspace, List.of("checkout", project.getAgentBranch()), "切换外部 Agent 分支失败");
        runRequired(workspace, List.of("merge", "--ff-only", trackedRemoteBranch), "外部 Agent 分支无法安全快进，请人工处理分支冲突");
        String latest = runRequired(workspace, List.of("rev-parse", "HEAD"), "读取外部 Agent 最新提交失败").output();
        project.setLatestCommit(latest);
        if (before != null && !before.equals(latest)) project.setStatus("EXTERNAL_AGENT_PUSHED");
        project.setRemoteStatus("SYNCED");
        project.setUpdatedAt(LocalDateTime.now().toString());
        project.setMessage(before != null && !before.equals(latest)
                ? "已同步外部 Agent 的最新提交，可查看版本或下载代码 ZIP"
                : "已刷新 Gitee，外部 Agent 分支暂未出现新提交");
        store.save(project);
        return gitService.get(mode, sourceId);
    }

    private AgentGitProjectDocument required(String mode, long sourceId) {
        AgentGitProjectDocument project = store.find(mode, sourceId);
        if (project == null) throw new IllegalArgumentException("请先准备本地 Git 复现项目");
        return project;
    }

    private GitCommandResult runRequired(Path workspace, List<String> args, String message) {
        try {
            GitCommandResult result = git.run(workspace, args);
            if (!result.successful()) throw new IllegalStateException(message + (result.output().isBlank() ? "" : "：" + result.output()));
            return result;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(message, e);
        } catch (Exception e) {
            if (e instanceof IllegalStateException state) throw state;
            throw new IllegalStateException(message, e);
        }
    }

    private GitCommandResult run(Path workspace, List<String> args) {
        try {
            return git.run(workspace, args);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("检查本地 Git 远程仓库时被中断", e);
        } catch (Exception e) {
            throw new IllegalStateException("检查本地 Git 远程仓库失败", e);
        }
    }
}
