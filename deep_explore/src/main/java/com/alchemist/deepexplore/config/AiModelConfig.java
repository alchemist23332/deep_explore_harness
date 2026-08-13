package com.alchemist.deepexplore.config;

import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiModelConfig {

    @Bean("fastStreamingChatModel")
    StreamingChatModel fastStreamingChatModel(AiModelProperties properties) {
        return buildModel(
                properties,
                properties.maxCompletionTokens(),
                properties.reasoningEffort()
        );
    }

    @Bean("deepStreamingChatModel")
    StreamingChatModel deepStreamingChatModel(AiModelProperties properties) {
        return buildModel(
                properties,
                properties.deepMaxCompletionTokens(),
                properties.deepReasoningEffort()
        );
    }

    private StreamingChatModel buildModel(
            AiModelProperties properties,
            int maxCompletionTokens,
            String reasoningEffort
    ) {
        String apiKey = properties.isConfigured() ? properties.apiKey() : "not-configured";

        return OpenAiStreamingChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(properties.baseUrl())
                .modelName(properties.modelName())
                .temperature(properties.temperature())
                .maxCompletionTokens(maxCompletionTokens)
                .reasoningEffort(reasoningEffort)
                .returnThinking(false)
                .timeout(properties.timeout())
                .logRequests(properties.logRequests())
                .logResponses(properties.logResponses())
                .build();
    }
}
