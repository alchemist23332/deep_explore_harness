package com.alchemist.deepexplore.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ToolExecutionConfig {

    @Bean(name = "toolExecutionExecutor", destroyMethod = "close")
    ExecutorService toolExecutionExecutor() {
        return Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual()
                        .name("agent-tool-", 0)
                        .factory()
        );
    }
}
