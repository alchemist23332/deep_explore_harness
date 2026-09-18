package com.alchemist.deepexplore.agent.adapter.langchain4j.tool.execution;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class ToolExecutionPropertiesTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withUserConfiguration(TestConfiguration.class);

    @Test
    void bindsBarrierConfiguration() {
        contextRunner.withPropertyValues(
                        "ai.agent.tool-execution.mode=BARRIER",
                        "ai.agent.tool-execution.max-parallelism=8",
                        "ai.agent.tool-execution.max-calls-per-round=64",
                        "ai.agent.tool-execution.barrier-timeout=120s"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    ToolExecutionProperties properties = context.getBean(
                            ToolExecutionProperties.class
                    );
                    assertThat(properties.barrierEnabled()).isTrue();
                    assertThat(properties.maxParallelism()).isEqualTo(8);
                    assertThat(properties.maxCallsPerRound()).isEqualTo(64);
                    assertThat(properties.barrierTimeout())
                            .isEqualTo(Duration.ofSeconds(120));
                });
    }

    @Test
    void rejectsInvalidParallelism() {
        contextRunner.withPropertyValues(
                        "ai.agent.tool-execution.mode=BARRIER",
                        "ai.agent.tool-execution.max-parallelism=0",
                        "ai.agent.tool-execution.max-calls-per-round=64",
                        "ai.agent.tool-execution.barrier-timeout=120s"
                )
                .run(context -> assertThat(context).hasFailed());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(ToolExecutionProperties.class)
    static class TestConfiguration {
    }
}
