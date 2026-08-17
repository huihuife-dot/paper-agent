package com.myagent.assistant.researchengineering.service;

import com.myagent.assistant.researchengineering.config.AgentDeliveryProperties;
import com.myagent.assistant.researchengineering.config.AgentExecutionProperties;
import com.myagent.assistant.researchengineering.dto.AgentGitProjectResponse;
import com.myagent.assistant.researchengineering.git.AgentGitProjectDocument;
import com.myagent.assistant.researchengineering.git.AgentGitProjectStore;
import com.myagent.assistant.researchengineering.git.GitCommandResult;
import com.myagent.assistant.researchengineering.git.GitCommandRunner;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class AgentGitService {
    private static final DateTimeFormatter BRANCH_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final Set<String> EXCLUDED_NAMES = Set.of(".git", ".env", "node_modules", "target", "dist", ".venv", "venv", "__pycache__");
    private static final long MAX_DELIVERY_BYTES = 100L * 1024 * 1024;

    private final GitCommandRunner git;
    private final AgentGitProjectStore store;
    private final AgentPackageService packageService;
    private final AgentDeliveryProperties properties;
    private final AgentExecutionProperties executionProperties;
    private final Path packageRoot;

    public AgentGitService(GitCommandRunner git, AgentGitProjectStore store, AgentPackageService packageService,
                           AgentDeliveryProperties properties, AgentExecutionProperties executionProperties) {
        this.git = git;
        this.store = store;
        this.packageService = packageService;
        this.properties = properties;
        this.executionProperties = executionProperties;
        this.packageRoot = Path.of(properties.getPackageRoot()).toAbsolutePath().normalize();
    }

    public AgentGitProjectResponse prepareManagedRun(String mode, long sourceId, Path workspace) {
        return prepareRun(mode, sourceId, workspace, "server", "RUNNING");
    }

    public AgentGitProjectResponse prepareExternalRun(String mode, long sourceId, String workspacePath) {
        Path workspace;
        if ("paper".equals(mode)) {
            Path root = Path.of(executionProperties.getWorkspaceRoot()).toAbsolutePath().normalize();
            workspace = root.resolve("paper-" + sourceId).normalize();
            ensureWithin(workspace, root, "论文工作区");
        } else if ("idea".equals(mode)) {
            if (workspacePath == null || workspacePath.isBlank()) throw new IllegalArgumentException("请填写服务器上的 Idea Git 工作区");
            Path root = Path.of(executionProperties.getIdeaWorkspaceRoot()).toAbsolutePath().normalize();
            workspace = Path.of(workspacePath).toAbsolutePath().normalize();
            ensureWithin(workspace, root, "Idea 工作区");
        } else {
            throw new IllegalArgumentException("不支持的项目模式");
        }
        return prepareRun(mode, sourceId, workspace, "external", "WAITING_EXTERNAL_AGENT");
    }

    private AgentGitProjectResponse prepareRun(String mode, long sourceId, Path workspace, String actor, String status) {
        try {
            Files.createDirectories(workspace);
            boolean existingRepo = Files.isDirectory(workspace.resolve(".git"));
            if ("idea".equals(mode) && !existingRepo) {
                throw new IllegalArgumentException("Idea 工作区必须是已有 Git 仓库，避免自动提交整份未知代码");
            }
            if (existingRepo && !run(workspace, "status", "--porcelain").output().isBlank()) {
                throw new IllegalStateException("工作区存在未提交修改，请先提交或暂存后再启动 Agent");
            }
            if (!existingRepo) initializeRepository(workspace);
            configureIdentity(workspace);
            checkoutDefaultBranch(workspace);
            if ("paper".equals(mode)) packageService.materializePaperTask(sourceId, workspace);
            else packageService.materializeIdeaTask(sourceId, workspace);
            ensureGitignore(workspace);
            ensureProjectReadme(workspace, mode, sourceId);
            commitIfChanged(workspace, "chore: refresh MyAgent task package");
            String baseline = head(workspace);
            String branch = "agent/" + actor + "/" + mode + "-" + sourceId + "-" + BRANCH_TIME.format(LocalDateTime.now())
                    + "-" + UUID.randomUUID().toString().substring(0, 4);
            require(workspace, List.of("checkout", "-b", branch), "创建 Agent 任务分支失败");

            AgentGitProjectDocument project = store.find(mode, sourceId);
            if (project == null) project = new AgentGitProjectDocument();
            project.setProjectId(mode + "-" + sourceId);
            project.setMode(mode);
            project.setSourceId(sourceId);
            project.setWorkspace(workspace.toAbsolutePath().normalize().toString());
            project.setStatus(status);
            project.setBaselineCommit(baseline);
            project.setAgentBranch(branch);
            project.setLatestCommit(baseline);
            if (project.getRemoteStatus() == null) project.setRemoteStatus("LOCAL_ONLY");
            project.setTaskPackageVersion(AgentPackageService.PACKAGE_VERSION);
            project.setUpdatedAt(LocalDateTime.now().toString());
            project.setMessage("已建立任务基线和独立 " + ("server".equals(actor) ? "平台" : "外部") + " Agent 分支");
            return response(store.save(project));
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("准备本地 Git 工作区失败", e);
        }
    }

    public AgentGitProjectResponse commitManagedResult(String mode, long sourceId, Path workspace, String status) {
        AgentGitProjectDocument project = required(mode, sourceId);
        try {
            commitIfChanged(workspace, "agent: complete " + mode + " " + sourceId + " delivery");
            project.setLatestCommit(head(workspace));
            project.setStatus(status);
            project.setUpdatedAt(LocalDateTime.now().toString());
            project.setMessage("COMPLETED".equals(status) ? "Agent 结果已提交到本地 Git 分支" : "Agent 执行结束，当前工作区检查点已保存");
            return response(store.save(project));
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("提交 Agent 结果失败", e);
        }
    }

    public AgentGitProjectResponse get(String mode, long sourceId) { return response(required(mode, sourceId)); }

    public List<AgentGitProjectResponse> list() { return store.list().stream().map(this::response).toList(); }

    public Path createDeliveryZip(String mode, long sourceId) {
        AgentGitProjectDocument project = required(mode, sourceId);
        Path workspace = Path.of(project.getWorkspace()).toAbsolutePath().normalize();
        String packageId = "delivery-" + mode + "-" + sourceId + "-" + shortCommit(project.getLatestCommit());
        Path target = packageRoot.resolve(packageId + ".zip").normalize();
        try {
            String status = require(workspace, List.of("status", "--porcelain"), "检查 Git 工作区失败").output();
            if (!status.isBlank()) {
                throw new IllegalStateException("工作区存在未提交修改，请先完成提交后再下载代码成果包");
            }
            String currentCommit = head(workspace);
            if (project.getLatestCommit() == null || !currentCommit.equals(project.getLatestCommit())) {
                throw new IllegalStateException("当前工作区版本与平台记录不一致，请先刷新或提交 Agent 结果");
            }
            List<String> trackedFiles = require(workspace,
                    List.of("-c", "core.quotepath=false", "ls-files"), "读取 Git 文件清单失败").output()
                    .lines().filter(line -> !line.isBlank()).toList();
            Files.createDirectories(packageRoot);
            try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(target), StandardCharsets.UTF_8)) {
                long total = 0;
                for (String entryName : trackedFiles) {
                    Path relative = Path.of(entryName).normalize();
                    Path file = workspace.resolve(relative).normalize();
                    if (!file.startsWith(workspace) || !Files.isRegularFile(file) || containsExcludedPart(relative)) continue;
                    total += Files.size(file);
                    if (total > MAX_DELIVERY_BYTES) throw new IOException("代码成果包超过 100MB 限制");
                    zip.putNextEntry(new ZipEntry(relative.toString().replace('\\', '/')));
                    Files.copy(file, zip);
                    zip.closeEntry();
                }
            }
            return target;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new IllegalStateException("生成代码成果 ZIP 失败", e);
        }
    }

    private void initializeRepository(Path workspace) throws IOException, InterruptedException {
        GitCommandResult result = git.run(workspace, List.of("init", "-b", "main"));
        if (!result.successful()) {
            require(workspace, List.of("init"), "初始化 Git 仓库失败");
            require(workspace, List.of("branch", "-M", "main"), "设置 main 分支失败");
        }
    }

    private void configureIdentity(Path workspace) throws IOException, InterruptedException {
        require(workspace, List.of("config", "user.name", properties.getGitUserName()), "配置 Git 用户名失败");
        require(workspace, List.of("config", "user.email", properties.getGitUserEmail()), "配置 Git 邮箱失败");
    }

    private void checkoutDefaultBranch(Path workspace) throws IOException, InterruptedException {
        GitCommandResult head = git.run(workspace, List.of("rev-parse", "--verify", "HEAD"));
        if (!head.successful()) return;
        GitCommandResult main = git.run(workspace, List.of("show-ref", "--verify", "--quiet", "refs/heads/main"));
        if (main.successful()) require(workspace, List.of("checkout", "main"), "切换 main 分支失败");
    }

    private void ensureGitignore(Path workspace) throws IOException {
        Path file = workspace.resolve(".gitignore");
        if (Files.exists(file)) return;
        Files.writeString(file, """
                .env
                .env.*
                !.env.example
                **/secrets/**
                node_modules/
                target/
                dist/
                .venv/
                venv/
                __pycache__/
                *.pyc
                *.key
                *.pem
                """);
    }

    private void ensureProjectReadme(Path workspace, String mode, long sourceId) throws IOException {
        Path file = workspace.resolve("README.md");
        if (Files.exists(file)) return;
        Files.writeString(file, """
                # MyAgent 复现代码项目

                本仓库由 MyAgent 创建。任务输入位于 `task/`，证据位于 `evidence/`。

                外部 Agent 应先阅读 `task/README-使用说明.md`、`task/task-package.json` 和 `manifest.json`，
                在当前 Agent 分支完成代码与短检查，并填写 `handoff/handoff-template.json`。

                不要提交 API Key、`.env`、数据集、模型大文件、依赖目录或构建产物，也不要强制推送主分支。

                项目来源：%s #%d
                """.formatted(mode, sourceId));
    }

    private void commitIfChanged(Path workspace, String message) throws IOException, InterruptedException {
        require(workspace, List.of("add", "-A"), "暂存 Git 修改失败");
        GitCommandResult diff = git.run(workspace, List.of("diff", "--cached", "--quiet"));
        if (diff.exitCode() == 1) require(workspace, List.of("commit", "-m", message), "提交 Git 修改失败");
        else if (diff.exitCode() != 0) throw new IllegalStateException("检查 Git 修改失败：" + diff.output());
    }

    private String head(Path workspace) throws IOException, InterruptedException {
        return require(workspace, List.of("rev-parse", "HEAD"), "读取 Git 提交失败").output();
    }

    private GitCommandResult run(Path workspace, String... args) throws IOException, InterruptedException {
        return git.run(workspace, List.of(args));
    }

    private GitCommandResult require(Path workspace, List<String> args, String message) throws IOException, InterruptedException {
        GitCommandResult result = git.run(workspace, args);
        if (!result.successful()) throw new IllegalStateException(message + (result.output().isBlank() ? "" : "：" + result.output()));
        return result;
    }

    private AgentGitProjectDocument required(String mode, long sourceId) {
        AgentGitProjectDocument project = store.find(mode, sourceId);
        if (project == null) throw new IllegalArgumentException("尚未建立该复现项目的 Git 工作区");
        return project;
    }

    private AgentGitProjectResponse response(AgentGitProjectDocument project) {
        Path workspace = Path.of(project.getWorkspace());
        String delivery = project.getLatestCommit() == null ? null : "/api/agent-git-projects/" + project.getMode() + "/"
                + project.getSourceId() + "/delivery.zip";
        return new AgentGitProjectResponse(project.getProjectId(), project.getMode(), project.getSourceId(),
                workspace.getFileName().toString(), project.getStatus(), project.getBaselineCommit(), project.getAgentBranch(),
                project.getLatestCommit(), project.getRemoteProvider(), project.getRemoteUrl(), project.getRemoteStatus(),
                project.getRemoteBranches() == null ? List.of() : project.getRemoteBranches(),
                project.getTaskPackageVersion(), project.getMessage(), project.getUpdatedAt(), delivery);
    }

    private boolean excluded(String name) { return EXCLUDED_NAMES.contains(name) || name.startsWith(".env.") || name.endsWith(".key") || name.endsWith(".pem"); }
    private boolean containsExcludedPart(Path path) {
        for (Path part : path) if (excluded(part.toString())) return true;
        return false;
    }
    private String shortCommit(String commit) { return commit == null || commit.length() < 8 ? "unknown" : commit.substring(0, 8); }

    private void ensureWithin(Path path, Path root, String label) {
        try {
            Files.createDirectories(root);
            Path realRoot = root.toRealPath();
            Path realPath = Files.exists(path) ? path.toRealPath() : path;
            if (!realPath.startsWith(realRoot)) throw new IllegalArgumentException(label + "必须位于配置根目录内");
        } catch (IOException e) {
            throw new IllegalArgumentException("无法验证" + label, e);
        }
    }
}
