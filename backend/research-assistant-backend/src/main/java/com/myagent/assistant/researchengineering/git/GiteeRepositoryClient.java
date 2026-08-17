package com.myagent.assistant.researchengineering.git;

public interface GiteeRepositoryClient {
    GiteeRepository createPrivateRepository(String name, String description);
}
