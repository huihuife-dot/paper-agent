package com.myagent.assistant.researchengineering.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myagent.assistant.researchengineering.config.AgentDeliveryProperties;
import com.myagent.assistant.researchengineering.dto.AgentEvidenceResponse;
import com.myagent.assistant.researchengineering.dto.AgentPackageResponse;
import com.myagent.assistant.researchengineering.dto.AgentTaskPackageResponse;
import com.myagent.assistant.researchengineering.dto.IdeaImprovementContextResponse;
import com.myagent.assistant.researchengineering.dto.PaperReproductionContextResponse;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Creates provider-neutral, versioned Agent task packages. */
@Service
public class AgentPackageService {
    public static final String PACKAGE_VERSION = "research-agent-package-v1";
    private static final DateTimeFormatter ID_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final ResearchEngineeringContextService contextService;
    private final ObjectMapper mapper;
    private final Path packageRoot;

    public AgentPackageService(ResearchEngineeringContextService contextService,
                               ObjectMapper mapper,
                               AgentDeliveryProperties properties) {
        this.contextService = contextService;
        this.mapper = mapper;
        this.packageRoot = Path.of(properties.getPackageRoot()).toAbsolutePath().normalize();
    }

    public AgentPackageResponse createPaperPackage(long paperId) {
        PaperReproductionContextResponse context = contextService.getPaperReproductionContext(paperId, 2);
        return create("paper", paperId, context.getSourceRevision(), paperFiles(context));
    }

    public AgentPackageResponse createIdeaPackage(long ideaId) {
        IdeaImprovementContextResponse context = contextService.getIdeaImprovementContext(ideaId);
        return create("idea", ideaId, context.getSourceRevision(), ideaFiles(context));
    }

    /** Writes the exact same portable task material into a managed Agent workspace. */
    public void materializePaperTask(long paperId, Path workspace) {
        PaperReproductionContextResponse context = contextService.getPaperReproductionContext(paperId, 2);
        writeFiles(workspace, "paper", paperId, context.getSourceRevision(), paperFiles(context));
    }

    public void materializeIdeaTask(long ideaId, Path workspace) {
        IdeaImprovementContextResponse context = contextService.getIdeaImprovementContext(ideaId);
        writeFiles(workspace, "idea", ideaId, context.getSourceRevision(), ideaFiles(context));
    }

    public Resource packageResource(String packageId) {
        if (packageId == null || !packageId.matches("[a-z0-9-]{12,120}")) {
            throw new IllegalArgumentException("非法任务包编号");
        }
        try {
            Path file = packageRoot.resolve(packageId + ".zip").normalize();
            if (!file.startsWith(packageRoot) || !Files.isRegularFile(file)) throw new IllegalArgumentException("任务包不存在");
            return new UrlResource(file.toUri());
        } catch (IOException e) {
            throw new IllegalStateException("无法读取任务包", e);
        }
    }

    private AgentPackageResponse create(String mode, long sourceId, String sourceRevision, Map<String, byte[]> payload) {
        String packageId = mode + "-" + sourceId + "-" + ID_TIME.format(LocalDateTime.now()) + "-"
                + UUID.randomUUID().toString().substring(0, 8);
        Map<String, byte[]> files = withManifest(mode, sourceId, sourceRevision, payload, packageId);
        try {
            Files.createDirectories(packageRoot);
            Path target = packageRoot.resolve(packageId + ".zip");
            Path temporary = packageRoot.resolve(packageId + ".zip.tmp");
            Files.write(temporary, zip(files));
            Files.move(temporary, target);
            return new AgentPackageResponse(packageId, mode.equals("paper") ? "PAPER_REPRODUCTION" : "IDEA_CODE_IMPROVEMENT",
                    sourceId, sourceRevision, PACKAGE_VERSION, target.getFileName().toString(),
                    "/api/agent-packages/" + packageId + "/download", LocalDateTime.now().toString());
        } catch (IOException e) {
            throw new IllegalStateException("生成复现任务包失败", e);
        }
    }

    private Map<String, byte[]> paperFiles(PaperReproductionContextResponse context) {
        Map<String, byte[]> files = commonFiles(context.getTaskPackage(), context.getEvidence());
        putJson(files, "paper/paper-metadata.json", context.getPaper());
        putJson(files, "task/reproduction-spec.json", context.getReproductionSpec());
        return files;
    }

    private Map<String, byte[]> ideaFiles(IdeaImprovementContextResponse context) {
        Map<String, byte[]> files = commonFiles(context.getTaskPackage(), context.getEvidence());
        putJson(files, "task/idea.json", context.getIdea());
        putJson(files, "task/repository-baseline.template.json", Map.of(
                "repositoryRoot", "由外部 Agent 填写", "allowedPaths", List.of("src/**", "tests/**"),
                "prohibitedPaths", List.of(".git/**", ".env", "**/secrets/**"),
                "basicChecks", List.of("由外部 Agent 根据真实仓库填写")));
        return files;
    }

    private Map<String, byte[]> commonFiles(AgentTaskPackageResponse taskPackage, List<AgentEvidenceResponse> evidence) {
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("README-使用说明.md", readme().getBytes(StandardCharsets.UTF_8));
        putJson(files, "task/task-package.json", taskPackage);
        putJson(files, "evidence/evidence.json", evidence == null ? List.of() : evidence);
        putJson(files, "evidence/conflicts.json", taskPackage == null ? List.of() : taskPackage.getConflicts());
        putJson(files, "evidence/missing-information.json", taskPackage == null ? List.of() : taskPackage.getMissingInformation());
        files.put("evidence/key-source-excerpts.md", evidenceMarkdown(evidence).getBytes(StandardCharsets.UTF_8));
        putJson(files, "handoff/handoff-template.json", Map.of(
                "status", "COMPLETED | PARTIAL | FAILED", "baseCommit", "", "resultCommit", "",
                "changedFiles", List.of(), "checks", List.of(), "evidenceBacked", List.of(),
                "safeDefaults", List.of(), "blocked", List.of()));
        return files;
    }

    private Map<String, byte[]> withManifest(String mode, long sourceId, String sourceRevision,
                                             Map<String, byte[]> payload, String packageId) {
        Map<String, byte[]> files = new LinkedHashMap<>(payload);
        List<Map<String, Object>> checksums = files.entrySet().stream().map(entry -> Map.<String, Object>of(
                "path", entry.getKey(), "sha256", sha256(entry.getValue()), "size", entry.getValue().length)).toList();
        putJson(files, "manifest.json", Map.of(
                "packageId", packageId, "packageVersion", PACKAGE_VERSION,
                "taskType", mode.equals("paper") ? "PAPER_REPRODUCTION" : "IDEA_CODE_IMPROVEMENT",
                "sourceId", sourceId, "sourceRevision", sourceRevision == null ? "" : sourceRevision,
                "generatedAt", LocalDateTime.now().toString(), "files", checksums));
        return files;
    }

    private void writeFiles(Path workspace, String mode, long sourceId, String sourceRevision, Map<String, byte[]> payload) {
        Map<String, byte[]> files = withManifest(mode, sourceId, sourceRevision, payload, "managed-" + mode + "-" + sourceId);
        try {
            for (Map.Entry<String, byte[]> entry : files.entrySet()) {
                String relative = "README-使用说明.md".equals(entry.getKey()) ? "task/README-使用说明.md" : entry.getKey();
                Path target = workspace.resolve(relative).normalize();
                if (!target.startsWith(workspace.normalize())) throw new IllegalStateException("任务包路径越界");
                Files.createDirectories(target.getParent());
                Files.write(target, entry.getValue());
            }
        } catch (IOException e) {
            throw new IllegalStateException("写入 Agent 工作区任务包失败", e);
        }
    }

    private void putJson(Map<String, byte[]> files, String path, Object value) {
        try {
            files.put(path, mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(value));
        } catch (IOException e) {
            throw new IllegalStateException("无法序列化任务包文件 " + path, e);
        }
    }

    private byte[] zip(Map<String, byte[]> files) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
            for (Map.Entry<String, byte[]> entry : files.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception e) {
            throw new IllegalStateException("无法计算任务包校验值", e);
        }
    }

    private String evidenceMarkdown(List<AgentEvidenceResponse> evidence) {
        StringBuilder text = new StringBuilder("# 关键证据摘录\n\n");
        if (evidence == null || evidence.isEmpty()) return text.append("当前没有可导出的证据。\n").toString();
        for (AgentEvidenceResponse item : evidence) {
            text.append("## ").append(item.getTitle() == null ? item.getId() : item.getTitle()).append("\n\n")
                    .append("- 证据 ID：").append(item.getId()).append("\n")
                    .append("- 来源：").append(item.getSourceReference()).append("\n");
            if (item.getPageNumber() != null) text.append("- 页码：").append(item.getPageNumber()).append("\n");
            text.append("\n").append(item.getContent() == null ? "" : item.getContent()).append("\n\n");
        }
        return text.toString();
    }

    private String readme() {
        return """
                # MyAgent 结构化复现任务包

                本任务包可以交给 Codex、Claude Code 或其他能够读取本地文件的 Agent。

                1. 先读取 `manifest.json`、`task/task-package.json` 和证据文件；
                2. 可信证据可以实现，冲突和模型推断不得冒充确定事实；
                3. 缺失参数应做成可配置项，并在 handoff 中标记为安全默认值；
                4. 代码、短测试和交接分别放入 `src/`、`tests/`、`handoff/`；
                5. 不自动下载数据、运行长训练、提交密钥或强制推送主分支。
                """;
    }
}
