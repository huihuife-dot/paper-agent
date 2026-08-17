package com.myagent.assistant.researchengineering.git;

import com.myagent.assistant.researchengineering.config.AgentDeliveryProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Component
public class ProcessGitCommandRunner implements GitCommandRunner {
    private final String gitCommand;

    public ProcessGitCommandRunner(AgentDeliveryProperties properties) {
        this.gitCommand = properties.getGitCommand();
    }

    @Override
    public GitCommandResult run(Path workspace, List<String> arguments) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add(gitCommand);
        command.addAll(arguments);
        Process process = new ProcessBuilder(command).directory(workspace.toFile()).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        return new GitCommandResult(process.waitFor(), output);
    }
}
