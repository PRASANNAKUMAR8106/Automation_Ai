package com.autoflow.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.support.TaskExecutorAdapter;
import org.springframework.scheduling.annotation.AsyncConfigurer;

import java.util.concurrent.Executors;

/**
 * High-performance Asynchronous Execution using Java 21 Virtual Threads.
 */
@Configuration
public class AsyncConfig implements AsyncConfigurer {

    @Bean(name = "virtualTaskExecutor")
    public AsyncTaskExecutor virtualTaskExecutor() {
        return new TaskExecutorAdapter(Executors.newVirtualThreadPerTaskExecutor());
    }

    @Override
    public AsyncTaskExecutor getAsyncExecutor() {
        return virtualTaskExecutor();
    }
}
