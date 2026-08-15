package com.alchemist.deepexplore.config;

import java.util.Map;

import com.alchemist.deepexplore.agent.adapter.langchain4j.StreamingAssistant;
import com.alchemist.deepexplore.agent.adapter.langchain4j.memory.LangChain4jMemoryManager;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
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

    @Bean("fastStreamingAssistant")
    StreamingAssistant fastStreamingAssistant(
            @Qualifier("fastStreamingChatModel") StreamingChatModel model,
            LangChain4jMemoryManager chatMemoryManager,
            @Value("${ai.agent.system-prompt}") String systemPrompt
    ) {
        return buildAssistant(model, chatMemoryManager, systemPrompt);
    }

    @Bean("deepStreamingAssistant")
    StreamingAssistant deepStreamingAssistant(
            @Qualifier("deepStreamingChatModel") StreamingChatModel model,
            LangChain4jMemoryManager chatMemoryManager,
            @Value("${ai.agent.system-prompt}") String systemPrompt
    ) {
        return buildAssistant(model, chatMemoryManager, systemPrompt);
    }

    private StreamingAssistant buildAssistant(
            StreamingChatModel model,
            LangChain4jMemoryManager chatMemoryManager,
            String systemPrompt
    ) {
        return AiServices.builder(StreamingAssistant.class)
                .streamingChatModel(model)
                .chatMemoryProvider(chatMemoryManager::create)
                .systemMessage(systemPrompt)
                .build();
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
