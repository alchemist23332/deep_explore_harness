package com.alchemist.deepexplore.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.alchemist.deepexplore.agent.domain.AgentProfile;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class AgentLoopPropertiesTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withUserConfiguration(PropertiesConfiguration.class);

    @Test
    void bindsIndependentFastAndDeepRoundLimits() {
        contextRunner
                .withPropertyValues(
                        "ai.agent.fast-max-tool-calling-round-trips=24",
                        "ai.agent.deep-max-tool-calling-round-trips=64"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    AgentLoopProperties properties = context.getBean(
                            AgentLoopProperties.class
                    );
                    assertThat(properties.maxToolCallingRoundTrips(
                            AgentProfile.FAST.id()
                    )).isEqualTo(24);
                    assertThat(properties.maxToolCallingRoundTrips(
                            AgentProfile.DEEP.id()
                    )).isEqualTo(64);
                });
    }

    @Test
    void rejectsOutOfRangeRoundLimit() {
        contextRunner
                .withPropertyValues(
                        "ai.agent.fast-max-tool-calling-round-trips=0",
                        "ai.agent.deep-max-tool-calling-round-trips=64"
                )
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void rejectsUnknownProfile() {
        AgentLoopProperties properties = new AgentLoopProperties(24, 64);

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        properties.maxToolCallingRoundTrips("unknown"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown");
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AgentLoopProperties.class)
    static class PropertiesConfiguration {
    }
}
