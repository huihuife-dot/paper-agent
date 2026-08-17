package com.myagent.assistant.researchengineering.service;

import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

@Component
public class ProcessAgentCommandRunner implements AgentCommandRunner {
    @Override
    public int run(List<String> command, Path workspace, Consumer<String> output, Consumer<Process> onStarted)
            throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command)
                .directory(workspace.toFile())
                .redirectErrorStream(true)
                .start();
        onStarted.accept(process);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) output.accept(line);
        }
        return process.waitFor();
    }
}
