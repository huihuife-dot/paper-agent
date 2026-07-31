package com.myagent.assistant.paper.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class MultimodalJobAsyncExecutor {
    private static final Logger log = LoggerFactory.getLogger(MultimodalJobAsyncExecutor.class);

    @Async("profileTaskExecutor")
    public void executeAsync(Runnable runnable) {
        try {
            runnable.run();
        } catch (Exception e) {
            log.error("多模态任务异步执行失败: {}", e.getMessage(), e);
        }
    }
}
