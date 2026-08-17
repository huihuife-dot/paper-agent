package com.myagent.assistant.researchengineering.git;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public interface GitCommandRunner {
    GitCommandResult run(Path workspace, List<String> arguments) throws IOException, InterruptedException;
}
