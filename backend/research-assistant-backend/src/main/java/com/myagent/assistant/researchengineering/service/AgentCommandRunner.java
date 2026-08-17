package com.myagent.assistant.researchengineering.service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

/** Adapter around the external CLI, kept injectable so service tests never start a real Agent. */
public interface AgentCommandRunner {
    int run(List<String> command, Path workspace, Consumer<String> output, Consumer<Process> onStarted)
            throws IOException, InterruptedException;
}
