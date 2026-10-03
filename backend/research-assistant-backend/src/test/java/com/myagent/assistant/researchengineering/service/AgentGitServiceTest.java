package com.myagent.assistant.researchengineering.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myagent.assistant.researchengineering.config.AgentDeliveryProperties;
import com.myagent.assistant.researchengineering.config.AgentExecutionProperties;
import com.myagent.assistant.researchengineering.dto.AgentGitProjectResponse;
import com.myagent.assistant.researchengineering.git.AgentGitProjectStore;
import com.myagent.assistant.researchengineering.git.ProcessGitCommandRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

class AgentGitServiceTest {
    @TempDir Path tempDir;

    @Test
    void createsBaselineAgentBranchResultCommitAndSafeDeliveryZip() throws Exception {
        AgentDeliveryProperties delivery = new AgentDeliveryProperties();
        delivery.setPackageRoot(tempDir.resolve("packages").toString());
        delivery.setProjectIndexFile(tempDir.resolve("projects.json").toString());
        AgentExecutionProperties execution = new AgentExecutionProperties();
        execution.setWorkspaceRoot(tempDir.resolve("workspaces").toString());
        AgentPackageService packages = mock(AgentPackageService.class);
        doAnswer(invocation -> {
            Path workspace = invocation.getArgument(1);
            Files.createDirectories(workspace.resolve("task"));
            Files.writeString(workspace.resolve("task/task-package.json"), "{\"taskType\":\"PAPER_REPRODUCTION\"}");
            Files.writeString(workspace.resolve("task/README-使用说明.md"), "请先阅读任务说明");
            return null;
        }).when(packages).materializePaperTask(org.mockito.ArgumentMatchers.eq(38L), org.mockito.ArgumentMatchers.any(Path.class));
        AgentGitProjectStore store = new AgentGitProjectStore(new ObjectMapper(), delivery);
        AgentGitService service = new AgentGitService(new ProcessGitCommandRunner(delivery), store, packages, delivery, execution);
        Path workspace = tempDir.resolve("workspaces/paper-38");

        AgentGitProjectResponse prepared = service.prepareManagedRun("paper", 38L, workspace);
        Files.createDirectories(workspace.resolve("src"));
        Files.writeString(workspace.resolve("src/main.py"), "print('ok')\n");
        Files.writeString(workspace.resolve(".env"), "SECRET=never-export\n");
        AgentGitProjectResponse completed = service.commitManagedResult("paper", 38L, workspace, "COMPLETED");
        Path zipPath = service.createDeliveryZip("paper", 38L);
        List<String> entries = new ArrayList<>();
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(zipPath))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) entries.add(entry.getName());
        }

        assertTrue(prepared.agentBranch().startsWith("agent/server/paper-38-"));
        assertNotEquals(prepared.baselineCommit(), completed.latestCommit());
        assertTrue(entries.contains("src/main.py"));
        assertTrue(entries.contains("task/README-使用说明.md"));
        assertFalse(entries.stream().anyMatch(name -> name.startsWith(".git/") || name.equals(".env")));

        // Starting a new task must not reuse the previous remote branch's successful push status.
        var project = store.find("paper", 38L);
        project.setRemoteProvider("GITEE");
        project.setRemoteStatus("PUSHED");
        store.save(project);
        var nextTask = service.prepareExternalRun("paper", 38L, null);
        assertNotEquals(completed.agentBranch(), nextTask.agentBranch());
        assertEquals("PUSH_PENDING", store.find("paper", 38L).getRemoteStatus());
    }
}
