package com.myagent.assistant.researchengineering.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myagent.assistant.researchengineering.config.AgentDeliveryProperties;
import com.myagent.assistant.researchengineering.dto.AgentEvidenceResponse;
import com.myagent.assistant.researchengineering.dto.AgentPackageResponse;
import com.myagent.assistant.researchengineering.dto.AgentTaskPackageResponse;
import com.myagent.assistant.researchengineering.dto.PaperReproductionContextResponse;
import com.myagent.assistant.researchengineering.dto.IdeaImprovementContextResponse;
import com.myagent.assistant.researchengineering.git.AgentGitProjectDocument;
import com.myagent.assistant.researchengineering.git.AgentGitProjectStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentPackageServiceTest {
    @TempDir Path tempDir;
    private final ObjectMapper mapper = new ObjectMapper();
    private AgentGitProjectStore store;
    private AgentPackageService service;
    private ResearchEngineeringContextService contexts;

    @BeforeEach
    void setup() {
        contexts = mock(ResearchEngineeringContextService.class);
        AgentDeliveryProperties properties = new AgentDeliveryProperties();
        properties.setPackageRoot(tempDir.resolve("packages").toString());
        properties.setProjectIndexFile(tempDir.resolve("projects.json").toString());
        store = new AgentGitProjectStore(mapper, properties);
        service = new AgentPackageService(contexts, mapper, properties, new AgentDeliveryInstructions(store));
        when(contexts.getPaperReproductionContext(38L, 2)).thenReturn(
                new PaperReproductionContextResponse("revision", null, List.of(), null));
        when(contexts.getIdeaImprovementContext(7L)).thenReturn(
                new IdeaImprovementContextResponse("idea-revision", null, List.of(), null));
    }

    @Test
    void exportsVersionedPaperPackageWithTaskEvidenceAndManifest() throws Exception {
        AgentEvidenceResponse evidence = new AgentEvidenceResponse("fact-1", "paper_reproduction_fact", "LSTM 层数", "两层 LSTM", "paper:38:p12");
        AgentTaskPackageResponse task = new AgentTaskPackageResponse("PAPER_REPRODUCTION", "最小复现",
                List.of("实现模型"), List.of(), List.of(), List.of(evidence), List.of(),
                new AgentTaskPackageResponse.CodeScope(List.of("src/**"), List.of(".env"), "受限范围"), List.of("运行短测试"));
        PaperReproductionContextResponse context = new PaperReproductionContextResponse("paper-38-rev-1",
                new PaperReproductionContextResponse.AgentPaperResponse(38L, "Demo Paper", "abstract", "paper:38", List.of(), "now"),
                List.of(evidence), task);
        when(contexts.getPaperReproductionContext(38L, 2)).thenReturn(context);
        AgentPackageResponse result = service.createPaperPackage(38L);
        List<String> entries = new ArrayList<>();
        try (InputStream input = service.packageResource(result.packageId()).getInputStream();
             ZipInputStream zip = new ZipInputStream(input)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) entries.add(entry.getName());
        }

        assertTrue(entries.contains("manifest.json"));
        assertTrue(entries.contains("task/task-package.json"));
        assertTrue(entries.contains("task/reproduction-spec.json"));
        assertTrue(entries.contains("evidence/evidence.json"));
        assertTrue(entries.contains("handoff/handoff-template.json"));
        assertTrue(entries.contains("task/delivery.json"));
    }

    @Test
    void unboundPackageExplicitlyUsesOfflineDelivery() throws Exception {
        Map<String, byte[]> files = exportedPaper();
        var delivery = mapper.readTree(files.get("task/delivery.json"));
        assertEquals("OFFLINE", delivery.path("deliveryMode").asText());
        assertFalse(delivery.path("pushAllowed").asBoolean());
        assertEquals("", delivery.path("repositoryUrl").asText());
        assertTrue(readme(files).contains("平台刷新不会接收这个 ZIP"));
        assertFalse(readme(files).contains("git push"));
    }

    @Test
    void boundPackageIncludesExactCoordinatesCommandsAndChecksumsWithoutServerSecrets() throws Exception {
        AgentGitProjectDocument project = bound("paper", 38L);
        project.setWorkspace("C:/private/server-workspace");
        project.setMessage("secret-from-server-error");
        project.setRemoteUrl("https://user:secret@gitee.com/owner/repo");
        project.setRemoteName("server-gitee-alias");
        store.save(project);
        Map<String, byte[]> files = exportedPaper();
        var delivery = mapper.readTree(files.get("task/delivery.json"));
        assertEquals("GIT_REMOTE", delivery.path("deliveryMode").asText());
        assertTrue(delivery.path("pushAllowed").asBoolean());
        assertEquals(project.getAgentBranch(), delivery.path("branch").asText());
        assertEquals(project.getBaselineCommit(), delivery.path("baselineCommit").asText());
        assertEquals(project.getLatestCommit(), delivery.path("expectedStartCommit").asText());
        assertEquals("origin", delivery.path("cloneRemoteName").asText());
        assertTrue(readme(files).contains("git push origin HEAD:refs/heads/" + project.getAgentBranch()));
        assertTrue(readme(files).contains("git merge-base --is-ancestor " + project.getLatestCommit() + " HEAD"));
        assertTrue(readme(files).contains("自行配置"));
        for (byte[] bytes : files.values()) {
            String content = new String(bytes, StandardCharsets.UTF_8);
            assertFalse(content.contains("secret"));
            assertFalse(content.contains("private/server-workspace"));
            assertFalse(content.contains("server-gitee-alias"));
        }
        assertFalse(files.keySet().stream().anyMatch(name -> name.startsWith(".git/") || name.endsWith(".key")));
        boolean deliveryCovered = false;
        for (var item : mapper.readTree(files.get("manifest.json")).path("files")) {
            String path = item.path("path").asText();
            assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(files.get(path))), item.path("sha256").asText());
            if (path.equals("task/delivery.json")) deliveryCovered = true;
        }
        assertTrue(deliveryCovered);
    }

    @Test
    void ideaPackageUsesItsOwnBranchNotThePaperProject() throws Exception {
        store.save(bound("paper", 38L));
        store.save(bound("idea", 7L));
        var files = unzip(service.createIdeaPackage(7L));
        assertEquals("agent/external/idea-7-test", mapper.readTree(files.get("task/delivery.json")).path("branch").asText());
        assertTrue(files.containsKey("task/repository-baseline.template.json"));
        assertFalse(readme(files).contains("paper-38"));
    }

    @Test
    void pendingPushDoesNotAdvertiseExecutablePushInstructions() throws Exception {
        var project = bound("paper", 38L);
        project.setRemoteStatus("PUSH_PENDING");
        store.save(project);
        var files = exportedPaper();
        assertEquals("REMOTE_NOT_READY", mapper.readTree(files.get("task/delivery.json")).path("deliveryMode").asText());
        assertFalse(mapper.readTree(files.get("task/delivery.json")).path("pushAllowed").asBoolean());
        assertFalse(readme(files).contains("git push"));
    }

    @Test
    void platformBranchCannotBeUsedByExternalAgent() throws Exception {
        var project = bound("paper", 38L);
        project.setAgentBranch("agent/server/paper-38-test");
        store.save(project);
        var files = exportedPaper();
        assertEquals("PLATFORM_MANAGED", mapper.readTree(files.get("task/delivery.json")).path("deliveryMode").asText());
        assertFalse(readme(files).contains("git push"));
    }

    @Test
    void rejectsCredentialsAndUnsafeRepositoryUrlsWithoutLeakingThem() throws Exception {
        for (String url : List.of("https://user:secret@gitee.com/owner/repo.git", "git@gitee.com:owner/repo.git?token=secret",
                "git@gitee.com:owner/repo.git\ngit push evil", "git@evil.example:owner/repo.git")) {
            var project = bound("paper", 38L);
            project.setRemoteSshUrl(url);
            store.save(project);
            var files = exportedPaper();
            assertEquals("INVALID_TARGET", mapper.readTree(files.get("task/delivery.json")).path("deliveryMode").asText());
            assertFalse(readme(files).contains(url));
            assertFalse(new String(files.get("task/delivery.json"), StandardCharsets.UTF_8).contains("secret"));
        }
    }

    @Test
    void rejectsWrongBranchAndMissingCommit() throws Exception {
        for (String branch : List.of("main", "agent/external/paper-99-test", "agent/external/paper-38-x;evil")) {
            var project = bound("paper", 38L);
            project.setAgentBranch(branch);
            store.save(project);
            assertEquals("INVALID_TARGET", mapper.readTree(exportedPaper().get("task/delivery.json")).path("deliveryMode").asText());
        }
        var project = bound("paper", 38L);
        project.setLatestCommit(null);
        store.save(project);
        assertEquals("INVALID_TARGET", mapper.readTree(exportedPaper().get("task/delivery.json")).path("deliveryMode").asText());
    }

    @Test
    void reexportReadsNewBindingAndBranchButOldZipRemainsASnapshot() throws Exception {
        var oldPackage = service.createPaperPackage(38L);
        var project = bound("paper", 38L);
        project.setAgentBranch("agent/external/paper-38-new-task");
        project.setRemoteStatus("SYNCED");
        store.save(project);
        assertEquals(project.getAgentBranch(), mapper.readTree(exportedPaper().get("task/delivery.json")).path("branch").asText());
        assertEquals("OFFLINE", mapper.readTree(unzip(oldPackage).get("task/delivery.json")).path("deliveryMode").asText());
    }

    @Test
    void materializationDoesNotFreezePreviousTaskDeliveryCoordinates() throws Exception {
        store.save(bound("paper", 38L));
        Path workspace = tempDir.resolve("new-workspace");
        service.materializePaperTask(38L, workspace);
        assertTrue(Files.exists(workspace.resolve("task/task-package.json")));
        assertFalse(Files.exists(workspace.resolve("task/delivery.json")));
    }

    private AgentGitProjectDocument bound(String mode, long id) {
        var project = new AgentGitProjectDocument();
        project.setProjectId(mode + "-" + id);
        project.setMode(mode);
        project.setSourceId(id);
        project.setRemoteProvider("GITEE");
        project.setRemoteSshUrl("git@gitee.com:owner/repo.git");
        project.setRemoteStatus("PUSHED");
        project.setAgentBranch("agent/external/" + mode + "-" + id + "-test");
        project.setBaselineCommit("a".repeat(40));
        project.setLatestCommit("b".repeat(40));
        return project;
    }

    private Map<String, byte[]> exportedPaper() throws Exception {
        return unzip(service.createPaperPackage(38L));
    }

    private Map<String, byte[]> unzip(AgentPackageResponse response) throws Exception {
        Map<String, byte[]> files = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(service.packageResource(response.packageId()).getInputStream())) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) files.put(entry.getName(), zip.readAllBytes());
        }
        return files;
    }

    private String readme(Map<String, byte[]> files) {
        return new String(files.get("README-使用说明.md"), StandardCharsets.UTF_8);
    }
}
