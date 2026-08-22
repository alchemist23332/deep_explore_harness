package com.alchemist.deepexplore.config;

import java.util.Map;

import com.alchemist.deepexplore.agent.adapter.langchain4j.StreamingAssistant;
import com.alchemist.deepexplore.agent.adapter.langchain4j.memory.LangChain4jMemoryManager;
import com.alchemist.deepexplore.agent.adapter.langchain4j.prompt.SystemPromptRenderer;
import com.alchemist.deepexplore.agent.adapter.langchain4j.tool.WebSearchToolAdapter;
import com.alchemist.deepexplore.agent.domain.AgentProfile;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
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
            SystemPromptRenderer systemPromptRenderer,
            ObjectProvider<WebSearchToolAdapter> webSearchTool,
            @Value("${ai.agent.max-tool-calling-round-trips:3}")
            int maxToolCallingRoundTrips
    ) {
        return buildAssistant(
                model,
                chatMemoryManager,
                systemPromptRenderer,
                webSearchTool.getIfAvailable(),
                maxToolCallingRoundTrips,
                AgentProfile.FAST
        );
    }

    @Bean("deepStreamingAssistant")
    StreamingAssistant deepStreamingAssistant(
            @Qualifier("deepStreamingChatModel") StreamingChatModel model,
            LangChain4jMemoryManager chatMemoryManager,
            SystemPromptRenderer systemPromptRenderer,
            ObjectProvider<WebSearchToolAdapter> webSearchTool,
            @Value("${ai.agent.max-tool-calling-round-trips:3}")
            int maxToolCallingRoundTrips
    ) {
        return buildAssistant(
                model,
                chatMemoryManager,
                systemPromptRenderer,
                webSearchTool.getIfAvailable(),
                maxToolCallingRoundTrips,
                AgentProfile.DEEP
        );
    }

    private StreamingAssistant buildAssistant(
            StreamingChatModel model,
            LangChain4jMemoryManager chatMemoryManager,
            SystemPromptRenderer systemPromptRenderer,
            WebSearchToolAdapter webSearchTool,
            int maxToolCallingRoundTrips,
            AgentProfile profile
    ) {
        String systemPrompt = systemPromptRenderer.render(profile);
        AiServices<StreamingAssistant> builder = AiServices.builder(
                        StreamingAssistant.class
                )
                .streamingChatModel(model)
                .chatMemoryProvider(chatMemoryManager::create)
                .systemMessageProvider(ignored -> systemPrompt)
                .maxToolCallingRoundTrips(maxToolCallingRoundTrips);
        if (webSearchTool != null) {
            builder.tools(webSearchTool);
        }
        return builder.build();
    }

    private StreamingChatModel buildModel(
            AiModelProperties properties,
            String modelName,
            int maxCompletionTokens,
            String reasoningEffort,
            String thinking
    ) {
        String apiKey = properties.isConfigured() ? properties.apiKey() : "not-configured";

        boolean preserveThinking = "enabled".equalsIgnoreCase(thinking);
        OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder builder = OpenAiStreamingChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(properties.baseUrl())
                .modelName(modelName)
                .temperature(properties.temperature())
                .maxCompletionTokens(maxCompletionTokens)
                .returnThinking(preserveThinking)
                .sendThinking(preserveThinking)
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
