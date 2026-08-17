package com.myagent.assistant.researchengineering.service;

import com.myagent.assistant.researchengineering.config.AgentExecutionProperties;
import com.myagent.assistant.researchengineering.dto.AgentExecutionRequest;
import com.myagent.assistant.researchengineering.dto.AgentExecutionStatusResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.task.TaskExecutor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class AgentExecutionServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void paperRunUsesOnlyConfiguredCliAndFixedSubcommands() {
        List<List<String>> commands = new ArrayList<>();
        AgentExecutionService service = service(commands);

        AgentExecutionStatusResponse status = service.startPaper(38, new AgentExecutionRequest("", null));

        assertEquals("COMPLETED", status.status());
        assertEquals(3, commands.size());
        assertEquals(List.of("configured-agent", "init"), commands.get(0).subList(0, 2));
        assertEquals(List.of("configured-agent", "fetch-context"), commands.get(1).subList(0, 2));
        assertEquals(List.of("configured-agent", "deliver"), commands.get(2).subList(0, 2));
        assertTrue(commands.get(2).contains("根据当前论文的可信证据完成最小可运行复现；先阅读上下文，只实现有依据的内容，执行基础检查并生成交接。"));
    }

    @Test
    void ideaRunRejectsWorkspaceOutsideConfiguredRoot() {
        AgentExecutionService service = service(new ArrayList<>());

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.startIdea(7, new AgentExecutionRequest("", tempDir.resolveSibling("outside").toString())));

        assertTrue(error.getMessage().contains("根目录"));
    }

    @Test
    void ideaRunRequiresRepositoryBaselineBeforeLaunchingAgent() throws Exception {
        AgentExecutionService service = service(new ArrayList<>());
        Path workspace = tempDir.resolve("ideas").resolve("existing-project");
        Files.createDirectories(workspace);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.startIdea(7, new AgentExecutionRequest("", workspace.toString())));

        assertTrue(error.getMessage().contains("repository-baseline.json"));
    }

    private AgentExecutionService service(List<List<String>> commands) {
        AgentExecutionProperties properties = new AgentExecutionProperties();
        properties.setCommand("configured-agent");
        properties.setWorkspaceRoot(tempDir.resolve("papers").toString());
        properties.setIdeaWorkspaceRoot(tempDir.resolve("ideas").toString());
        TaskExecutor direct = Runnable::run;
        AgentCommandRunner runner = (command, workspace, output, onStarted) -> {
            commands.add(List.copyOf(command));
            output.accept("completed " + command.get(1));
            return 0;
        };
        return new AgentExecutionService(properties, runner, mock(AgentGitService.class), direct);
    }
}
