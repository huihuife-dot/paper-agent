package com.myagent.assistant.paper.service;

import com.myagent.assistant.paper.entity.PaperProfileJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 文献画像异步执行器。
 *
 * 独立 Component 的目的是让 @Async 能通过 Spring AOP 代理生效。
 * 如果在 PaperProfileJobServiceImpl 内部自调用，@Async 会被 Spring 代理绕过。
 */
@Component
public class ProfileJobAsyncExecutor {

    private static final Logger log = LoggerFactory.getLogger(ProfileJobAsyncExecutor.class);

    /**
     * 在 profileTaskExecutor 线程池中异步执行画像生成。
     *
     * @param runnable 执行逻辑
     */
    @Async("profileTaskExecutor")
    public void executeAsync(Runnable runnable) {
        try {
            runnable.run();
        } catch (Exception e) {
            log.error("画像生成异步任务异常: {}", e.getMessage(), e);
            throw e;
        }
    }
}
