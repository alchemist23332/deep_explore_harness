package com.alchemist.deepexplore.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ai.model")
public record AiModelProperties(
        String provider,
        String apiKey,
        String baseUrl,
        String modelName,
        String deepModelName,
        double temperature,
        int maxCompletionTokens,
        String reasoningEffort,
        int deepMaxCompletionTokens,
        String deepReasoningEffort,
        String fastThinking,
        String deepThinking,
        Duration timeout,
        boolean logRequests,
        boolean logResponses
) {

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    public AiProvider providerType() {
        return AiProvider.from(provider);
    }

    public String resolvedDeepModelName() {
        return deepModelName == null || deepModelName.isBlank() ? modelName : deepModelName;
    }
}
