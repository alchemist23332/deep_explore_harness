package com.alchemist.deepexplore.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class AiModelPropertiesTest {

    @Test
    void resolvesDeepSeekProviderAndDedicatedDeepModel() {
        AiModelProperties properties = properties(
                "DEEPSEEK",
                "deepseek-v4-flash",
                "deepseek-v4-pro"
        );

        assertThat(properties.providerType()).isEqualTo(AiProvider.DEEPSEEK);
        assertThat(properties.resolvedDeepModelName()).isEqualTo("deepseek-v4-pro");
    }

    @Test
    void usesFastModelWhenDeepModelIsNotConfigured() {
        AiModelProperties properties = properties(
                "OLLAMA",
                "qwen3.5:2b",
                " "
        );

        assertThat(properties.providerType()).isEqualTo(AiProvider.OLLAMA);
        assertThat(properties.resolvedDeepModelName()).isEqualTo("qwen3.5:2b");
    }

    private AiModelProperties properties(
            String provider,
            String modelName,
            String deepModelName
    ) {
        return new AiModelProperties(
                provider,
                "test-key",
                "https://example.test",
                modelName,
                deepModelName,
                0.3,
                1024,
                "none",
                4096,
                "high",
                "disabled",
                "enabled",
                Duration.ofSeconds(60),
                false,
                false
        );
    }
}
