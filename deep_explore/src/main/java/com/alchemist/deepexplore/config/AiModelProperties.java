package com.alchemist.deepexplore.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ai.model")
public record AiModelProperties(
        String apiKey,
        String baseUrl,
        String modelName,
        double temperature,
        int maxCompletionTokens,
        String reasoningEffort,
        int deepMaxCompletionTokens,
        String deepReasoningEffort,
        Duration timeout,
        boolean logRequests,
        boolean logResponses
) {

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }
}
