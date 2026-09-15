package com.kblite.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 文档异步解析向量化线程池
 *
 * @author kb-agent-lite
 */
@Configuration
public class AsyncConfig {

    @Bean("knowledgeAsyncExecutor")
    public Executor knowledgeAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("kb-vectorize-");
        executor.initialize();
        return executor;
    }
}
