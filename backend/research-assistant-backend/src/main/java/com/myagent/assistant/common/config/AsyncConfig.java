package com.myagent.assistant.common.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.core.task.TaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步任务线程池配置。
 *
 * 当前用途：文献画像异步生成（PaperProfileJobService）。
 * 线程池由 Spring 管理生命周期，可通过 application.properties 调整大小。
 *
 * 拒绝策略：CallerRunsPolicy —— 线程池满时由调用线程执行，
 * 避免静默丢任务，但会阻塞 HTTP 请求（极少发生，queue-capacity=10 已预留缓冲）。
 */
@EnableAsync
@Configuration
public class AsyncConfig {

    private static final Logger log = LoggerFactory.getLogger(AsyncConfig.class);

    @Value("${async.core-pool-size:2}")
    private int corePoolSize;

    @Value("${async.max-pool-size:4}")
    private int maxPoolSize;

    @Value("${async.queue-capacity:10}")
    private int queueCapacity;

    @Value("${async.thread-name-prefix:myagent-async-}")
    private String threadNamePrefix;

    /**
     * 画像生成专用线程池。
     *
     * Bean 名 = "profileTaskExecutor"，@Async 中引用。
     */
    @Bean("profileTaskExecutor")
    public Executor profileTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix(threadNamePrefix);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy() {
            @Override
            public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
                log.warn("画像生成线程池已满，回退到调用线程执行");
                super.rejectedExecution(r, e);
            }
        });
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }

    /**
     * 结构化知识抽取会调用文本模型并写入多条知识记录，与普通画像任务隔离。
     */
    @Bean("knowledgeTaskExecutor")
    public Executor knowledgeTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(10);
        executor.setThreadNamePrefix("knowledge-build-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }

    /**
     * RAG SSE 连接专用线程池，避免长时间模型流占用画像任务线程。
     */
    @Bean("ragStreamTaskExecutor")
    public TaskExecutor ragStreamTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("rag-stream-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.initialize();
        return executor;
    }

    /**
     * 外部 Agent 可能持续数分钟，必须与 RAG 流和画像任务隔离。
     * 单机演示只允许运行一个任务，避免同一服务器账号并发修改工作区。
     */
    @Bean("agentExecutionTaskExecutor")
    public TaskExecutor agentExecutionTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(4);
        executor.setThreadNamePrefix("agent-execution-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.initialize();
        return executor;
    }
}
