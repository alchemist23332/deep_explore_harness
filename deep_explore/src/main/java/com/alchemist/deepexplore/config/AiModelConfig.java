package com.alchemist.deepexplore.config;

import java.util.Map;

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
                properties.modelName(),
                properties.maxCompletionTokens(),
                properties.reasoningEffort(),
                properties.fastThinking()
        );
    }

    @Bean("deepStreamingChatModel")
    StreamingChatModel deepStreamingChatModel(AiModelProperties properties) {
        return buildModel(
                properties,
                properties.resolvedDeepModelName(),
                properties.deepMaxCompletionTokens(),
                properties.deepReasoningEffort(),
                properties.deepThinking()
        );
    }

    private StreamingChatModel buildModel(
            AiModelProperties properties,
            String modelName,
            int maxCompletionTokens,
            String reasoningEffort,
            String thinking
    ) {
        String apiKey = properties.isConfigured() ? properties.apiKey() : "not-configured";

        OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder builder = OpenAiStreamingChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(properties.baseUrl())
                .modelName(modelName)
                .temperature(properties.temperature())
                .maxCompletionTokens(maxCompletionTokens)
                .returnThinking(false)
                .timeout(properties.timeout())
                .logRequests(properties.logRequests())
                .logResponses(properties.logResponses());

        if (reasoningEffort != null && !reasoningEffort.isBlank()) {
            builder.reasoningEffort(reasoningEffort);
        }
        if (properties.providerType() == AiProvider.DEEPSEEK
                && thinking != null
                && !thinking.isBlank()) {
            builder.customParameters(Map.of("thinking", Map.of("type", thinking)));
        }
        return builder.build();
    }
}
