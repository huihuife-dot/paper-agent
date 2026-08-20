package com.myagent.assistant.knowledge.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class KnowledgeBuildAsyncExecutor {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBuildAsyncExecutor.class);

    @Async("knowledgeTaskExecutor")
    public void execute(Runnable runnable) {
        try {
            runnable.run();
        } catch (Exception e) {
            log.error("结构化知识异步构建失败: {}", e.getMessage(), e);
        }
    }
}
