package com.myagent.assistant.researchengineering.service;

import com.myagent.assistant.researchengineering.config.AgentExecutionProperties;
import com.myagent.assistant.researchengineering.dto.AgentExecutionRequest;
import com.myagent.assistant.researchengineering.dto.AgentExecutionStatusResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runs the independently installed Research Engineering CLI on the same host as MyAgent.
 * The browser can supply only a natural-language request and (for Idea mode) a directory beneath
 * the configured root; it can never supply an executable, arguments, or an unrestricted path.
 */
@Service
public class AgentExecutionService {
    private static final String PAPER_REQUEST = "根据当前论文的可信证据完成最小可运行复现；先阅读上下文，只实现有依据的内容，执行基础检查并生成交接。";
    private static final String IDEA_REQUEST = "根据当前 Idea 与仓库基线完成一次聚焦、安全的代码改进；先检查现有代码，只修改允许路径，执行基础检查并生成交接。";

    private final AgentExecutionProperties properties;
    private final AgentCommandRunner commandRunner;
    private final AgentGitService gitService;
    private final TaskExecutor executor;
    private final Map<String, RunRecord> runs = new ConcurrentHashMap<>();

    public AgentExecutionService(AgentExecutionProperties properties,
                                 AgentCommandRunner commandRunner,
                                 AgentGitService gitService,
                                 @Qualifier("agentExecutionTaskExecutor") TaskExecutor executor) {
        this.properties = properties;
        this.commandRunner = commandRunner;
        this.gitService = gitService;
        this.executor = executor;
    }

    public AgentExecutionStatusResponse startPaper(long paperId, AgentExecutionRequest request) {
        Path root = absolute(properties.getWorkspaceRoot());
        Path workspace = root.resolve("paper-" + paperId).normalize();
        try {
            Files.createDirectories(root);
            Files.createDirectories(workspace);
        } catch (IOException e) {
            throw new IllegalStateException("无法创建论文 Agent 工作区", e);
        }
        ensureWithin(workspace, root, "论文工作区");
        gitService.prepareManagedRun("paper", paperId, workspace);
        return start("paper", paperId, workspace, safeRequest(request == null ? null : request.request(), PAPER_REQUEST), false);
    }

    public AgentExecutionStatusResponse startIdea(long ideaId, AgentExecutionRequest request) {
        if (request == null || request.workspacePath() == null || request.workspacePath().isBlank()) {
            throw new IllegalArgumentException("请填写服务器上已有的 Idea 代码目录");
        }
        Path root = absolute(properties.getIdeaWorkspaceRoot());
        Path workspace = absolute(request.workspacePath());
        ensureWithin(workspace, root, "Idea 代码目录");
        if (!Files.isDirectory(workspace)) throw new IllegalArgumentException("Idea 代码目录不存在");
        if (!Files.isRegularFile(workspace.resolve("repository-baseline.json"))) {
            throw new IllegalArgumentException("Idea 代码目录缺少 repository-baseline.json，无法确认允许修改范围");
        }
        gitService.prepareManagedRun("idea", ideaId, workspace);
        return start("idea", ideaId, workspace, safeRequest(request.request(), IDEA_REQUEST), true);
    }

    public AgentExecutionStatusResponse status(String mode, long sourceId) {
        RunRecord record = runs.get(key(mode, sourceId));
        if (record == null) throw new IllegalArgumentException("尚未启动该 Agent 任务");
        return record.response();
    }

    public AgentExecutionStatusResponse stop(String mode, long sourceId) {
        RunRecord record = runs.get(key(mode, sourceId));
        if (record == null) throw new IllegalArgumentException("尚未启动该 Agent 任务");
        synchronized (record) {
            if (!"RUNNING".equals(record.status)) return record.response();
            record.status = "PAUSED";
            record.message = "已请求停止当前 Agent 进程，工作区会保留。";
            if (record.process != null) record.process.destroyForcibly();
        }
        return record.response();
    }

    private AgentExecutionStatusResponse start(String mode, long sourceId, Path workspace, String request, boolean idea) {
        if (!properties.isEnabled()) throw new IllegalStateException("后端托管 Agent 已被禁用，请检查 app.agent-execution.enabled");
        if (properties.getCommand() == null || properties.getCommand().isBlank()) {
            throw new IllegalStateException("未配置 app.agent-execution.command");
        }
        String key = key(mode, sourceId);
        RunRecord record = new RunRecord(mode, sourceId, workspace.getFileName().toString());
        RunRecord previous = runs.putIfAbsent(key, record);
        if (previous != null && "RUNNING".equals(previous.status)) throw new IllegalStateException("该 Agent 任务正在运行中");
        if (previous != null) runs.put(key, record);
        executor.execute(() -> execute(record, workspace, request, idea));
        return record.response();
    }

    private void execute(RunRecord record, Path workspace, String request, boolean idea) {
        try {
            runStep(record, workspace, "初始化工作区", command("init", "--workspace", workspace.toString(), "--mode", record.mode, "--source-id", String.valueOf(record.sourceId)));
            List<String> context = command("fetch-context", "--workspace", workspace.toString(), "--server-url", properties.getServerUrl());
            if (idea) context.addAll(List.of("--baseline-file", workspace.resolve("repository-baseline.json").toString()));
            runStep(record, workspace, "读取 MyAgent 科研上下文", context);
            List<String> deliver = command("deliver", "--workspace", workspace.toString(), "--request", request);
            if (properties.getEnvFile() != null && !properties.getEnvFile().isBlank()) deliver.addAll(List.of("--env-file", properties.getEnvFile()));
            runStep(record, workspace, "Agent 代码交付", deliver);
            synchronized (record) {
                if ("RUNNING".equals(record.status)) {
                    record.status = "COMPLETED";
                    record.phase = "已完成";
                    record.message = "Agent 已完成本轮交付；代码和完整交接保留在服务器工作区。";
                    gitService.commitManagedResult(record.mode, record.sourceId, workspace, "COMPLETED");
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            failUnlessPaused(record, "Agent 执行被中断，工作区已保留。");
            saveCheckpoint(record, workspace);
        } catch (Exception e) {
            failUnlessPaused(record, "Agent 执行失败：" + safeMessage(e));
            saveCheckpoint(record, workspace);
        } finally {
            synchronized (record) { record.process = null; }
        }
    }

    private void runStep(RunRecord record, Path workspace, String phase, List<String> command) throws IOException, InterruptedException {
        synchronized (record) {
            if (!"RUNNING".equals(record.status)) throw new InterruptedException("任务已停止");
            record.phase = phase;
        }
        int exit = commandRunner.run(command, workspace, record::append, process -> { synchronized (record) { record.process = process; } });
        if (exit != 0) throw new IllegalStateException(phase + "返回退出码 " + exit);
    }

    private List<String> command(String... arguments) {
        List<String> command = new ArrayList<>();
        command.add(properties.getCommand().trim());
        command.addAll(List.of(arguments));
        return command;
    }

    private Path absolute(String value) { return Path.of(value).toAbsolutePath().normalize(); }

    private void ensureWithin(Path path, Path root, String label) {
        try {
            Path realRoot = Files.exists(root) ? root.toRealPath() : root;
            Path realPath = Files.exists(path) ? path.toRealPath() : path;
            if (!realPath.startsWith(realRoot)) throw new IllegalArgumentException(label + "必须位于服务器配置的根目录内");
        } catch (IOException e) {
            throw new IllegalArgumentException("无法验证" + label + "的真实路径", e);
        }
    }

    private String safeRequest(String value, String fallback) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) return fallback;
        if (normalized.length() > 2000) throw new IllegalArgumentException("Agent 请求最多 2000 个字符");
        return normalized;
    }

    private void failUnlessPaused(RunRecord record, String message) {
        synchronized (record) {
            if (!"PAUSED".equals(record.status)) {
                record.status = "FAILED";
                record.phase = "失败";
                record.message = message;
            }
        }
    }

    private void saveCheckpoint(RunRecord record, Path workspace) {
        try {
            gitService.commitManagedResult(record.mode, record.sourceId, workspace, record.status);
        } catch (Exception ignored) {
            record.append("无法保存 Git 检查点：" + ignored.getMessage());
        }
    }

    private String safeMessage(Exception e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
    }

    private String key(String mode, long sourceId) { return mode + ":" + sourceId; }

    private final class RunRecord {
        private final String mode;
        private final long sourceId;
        private final String workspaceName;
        private final List<String> output = new ArrayList<>();
        private String status = "RUNNING";
        private String phase = "等待执行";
        private String message = "后端已接收任务，正在启动 Agent。";
        private Process process;

        private RunRecord(String mode, long sourceId, String workspaceName) {
            this.mode = mode;
            this.sourceId = sourceId;
            this.workspaceName = workspaceName;
        }

        private synchronized void append(String line) {
            int max = Math.max(10, properties.getOutputLines());
            output.add(line);
            while (output.size() > max) output.remove(0);
        }

        private synchronized AgentExecutionStatusResponse response() {
            return new AgentExecutionStatusResponse(mode, sourceId, status, phase, workspaceName, message,
                    List.copyOf(output), "RUNNING".equals(status));
        }
    }
}
