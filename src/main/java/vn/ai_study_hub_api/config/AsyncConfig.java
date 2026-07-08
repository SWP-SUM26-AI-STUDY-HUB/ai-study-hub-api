package vn.ai_study_hub_api.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Value("${app.async.core-pool-size:5}")
    private int corePoolSize;

    @Value("${app.async.max-pool-size:20}")
    private int maxPoolSize;

    @Value("${app.async.queue-capacity:100}")
    private int queueCapacity;

    @Value("${app.async.thread-name-prefix:doc-async-}")
    private String threadNamePrefix;

    @Bean(name = "taskExecutor", destroyMethod = "shutdown")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix(threadNamePrefix);
        // Java 21 virtual threads: each @Async task (RAG WebClient .block(), S3 upload)
        // runs on its own virtual thread instead of an OS thread, so blocking I/O never
        // exhausts the small 5/20 carrier pool. Pairs with spring.threads.virtual.enabled.
        executor.setVirtualThreads(true);
        // Finish in-flight document processing/callbacks on shutdown instead of cutting
        // them off mid-RAG-call (prevents orphaned PENDING/PROCESSING documents on deploy).
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        // Caller-runs: when the queue is full, the submitting thread runs the task itself
        // (observable back-pressure) instead of throwing TaskRejectedException.
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
