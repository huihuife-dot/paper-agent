package com.myagent.assistant.researchengineering.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myagent.assistant.researchengineering.config.AgentDeliveryProperties;
import com.myagent.assistant.researchengineering.dto.GiteePublishRequest;
import com.myagent.assistant.researchengineering.git.AgentGitProjectDocument;
import com.myagent.assistant.researchengineering.git.AgentGitProjectStore;
import com.myagent.assistant.researchengineering.git.GitCommandResult;
import com.myagent.assistant.researchengineering.git.GitCommandRunner;
import com.myagent.assistant.researchengineering.git.GiteeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;

class AgentGitRemoteServiceTest {
    @TempDir Path tempDir;

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
