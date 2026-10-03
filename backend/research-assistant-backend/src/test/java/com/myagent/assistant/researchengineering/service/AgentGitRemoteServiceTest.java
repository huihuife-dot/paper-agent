package com.myagent.assistant.researchengineering.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myagent.assistant.researchengineering.config.AgentDeliveryProperties;
import com.myagent.assistant.researchengineering.config.AgentExecutionProperties;
import com.myagent.assistant.researchengineering.dto.PaperReproductionContextResponse;
import com.myagent.assistant.researchengineering.dto.GiteePublishRequest;
import com.myagent.assistant.researchengineering.git.AgentGitProjectDocument;
import com.myagent.assistant.researchengineering.git.AgentGitProjectStore;
import com.myagent.assistant.researchengineering.git.GitCommandResult;
import com.myagent.assistant.researchengineering.git.GitCommandRunner;
import com.myagent.assistant.researchengineering.git.GiteeRepository;
import com.myagent.assistant.researchengineering.git.ProcessGitCommandRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentGitRemoteServiceTest {
    @TempDir Path tempDir;

    @Test
    void exportedBranchGuidesExternalCommitAndPlatformFastForwardUsingLocalRemote() throws Exception {
        // Use a temporary bare repository as the remote; no account, network or user repository is touched.
        ObjectMapper mapper = new ObjectMapper();
        AgentDeliveryProperties properties = new AgentDeliveryProperties();
        properties.setPackageRoot(tempDir.resolve("packages").toString());
        properties.setProjectIndexFile(tempDir.resolve("projects.json").toString());
        AgentExecutionProperties execution = new AgentExecutionProperties();
        execution.setWorkspaceRoot(tempDir.resolve("workspaces").toString());
        var store = new AgentGitProjectStore(mapper, properties);
        var contexts = mock(ResearchEngineeringContextService.class);
        when(contexts.getPaperReproductionContext(38L, 2)).thenReturn(
                new PaperReproductionContextResponse("rev", null, List.of(), null));
        var packages = new AgentPackageService(contexts, mapper, properties, new AgentDeliveryInstructions(store));
        var git = new ProcessGitCommandRunner(properties);
        var local = new AgentGitService(git, store, packages, properties, execution);
        local.prepareExternalRun("paper", 38L, null);
        var project = store.find("paper", 38L);
        Path workspace = Path.of(project.getWorkspace());
        Path remote = tempDir.resolve("remote.git");
        success(git, tempDir, "init", "--bare", remote.toString());
        success(git, workspace, "remote", "add", "gitee", remote.toString());
        success(git, workspace, "push", "gitee", "main", project.getAgentBranch());
        project.setRemoteProvider("GITEE");
        project.setRemoteSshUrl("git@gitee.com:test/roundtrip.git");
        project.setRemoteName("gitee");
        project.setRemoteStatus("PUSHED");
        store.save(project);

        var exported = packages.createPaperPackage(38L);
        com.fasterxml.jackson.databind.JsonNode delivery = null;
        try (var zip = new ZipInputStream(packages.packageResource(exported.packageId()).getInputStream())) {
            java.util.zip.ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.getName().equals("task/delivery.json")) delivery = mapper.readTree(zip.readAllBytes());
            }
        }
        org.junit.jupiter.api.Assertions.assertNotNull(delivery);
        assertTrue(delivery.path("pushAllowed").asBoolean());
        String branch = delivery.path("branch").asText();
        Path external = tempDir.resolve("external");
        success(git, tempDir, "clone", "--single-branch", "--branch", branch, remote.toString(), external.toString());
        success(git, external, "merge-base", "--is-ancestor", delivery.path("expectedStartCommit").asText(), "HEAD");
        success(git, external, "config", "user.name", "Package test");
        success(git, external, "config", "user.email", "test@example.invalid");
        Files.writeString(external.resolve("result.py"), "print('external result')\n");
        success(git, external, "add", "result.py");
        success(git, external, "commit", "-m", "external delivery");
        success(git, external, "push", "origin", "HEAD:refs/heads/" + branch);
        String expectedCommit = success(git, external, "rev-parse", "HEAD");

        var remoteService = new AgentGitRemoteService((name, description) -> { throw new AssertionError("No remote API calls"); },
                git, store, local, properties);
        var synced = remoteService.refresh("paper", 38L);
        assertEquals(expectedCommit, synced.latestCommit());
        assertEquals("EXTERNAL_AGENT_PUSHED", synced.status());
        // Git may check out CRLF on Windows; compare the source text, not OS line endings.
        assertEquals("print('external result')\n", Files.readString(workspace.resolve("result.py")).replace("\r\n", "\n"));
        assertEquals(project.getBaselineCommit(), success(git, workspace, "rev-parse", "main"));
    }

    private String success(GitCommandRunner git, Path workspace, String... arguments) throws Exception {
        var result = git.run(workspace, List.of(arguments));
        assertEquals(0, result.exitCode(), result.output());
        return result.output().trim();
    }

    @Test
    void createsPrivateGiteeRepositoryAndPushesOnlyMainAndTaskBranch() throws Exception {
        AgentDeliveryProperties properties = new AgentDeliveryProperties();
        properties.setProjectIndexFile(tempDir.resolve("projects.json").toString());
        properties.setGiteeRemoteName("gitee");
        AgentGitProjectStore store = new AgentGitProjectStore(new ObjectMapper(), properties);
        Path workspace = tempDir.resolve("paper-38");
        Files.createDirectories(workspace);
        AgentGitProjectDocument project = new AgentGitProjectDocument();
        project.setProjectId("paper-38"); project.setMode("paper"); project.setSourceId(38L);
        project.setWorkspace(workspace.toString()); project.setAgentBranch("agent/external/paper-38-demo");
        store.save(project);
        List<List<String>> commands = new ArrayList<>();
        GitCommandRunner git = (path, arguments) -> {
            commands.add(List.copyOf(arguments));
            if (arguments.equals(List.of("remote", "get-url", "gitee"))) return new GitCommandResult(2, "No such remote");
            return new GitCommandResult(0, "");
        };
        AgentGitRemoteService service = new AgentGitRemoteService(
                (name, description) -> new GiteeRepository("owner/" + name, "https://gitee.com/owner/" + name, "git@gitee.com:owner/" + name + ".git"),
                git, store, mock(AgentGitService.class), properties);

        service.publish("paper", 38L, new GiteePublishRequest("paper-38", "demo"));

        assertEquals("PUSHED", store.find("paper", 38L).getRemoteStatus());
        assertTrueCommand(commands, List.of("remote", "add", "gitee", "git@gitee.com:owner/paper-38.git"));
        assertTrueCommand(commands, List.of("push", "-u", "gitee", "main"));
        assertTrueCommand(commands, List.of("push", "-u", "gitee", "agent/external/paper-38-demo"));
        assertFalse(commands.toString().contains("token"));
    }

    private void assertTrueCommand(List<List<String>> commands, List<String> expected) {
        org.junit.jupiter.api.Assertions.assertTrue(commands.contains(expected));
    }
}
