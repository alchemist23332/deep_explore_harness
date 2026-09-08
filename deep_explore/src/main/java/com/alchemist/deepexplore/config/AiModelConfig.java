package com.alchemist.deepexplore.config;

import java.util.Map;

import com.alchemist.deepexplore.agent.adapter.langchain4j.AgentProfileRuntime;
import com.alchemist.deepexplore.agent.adapter.langchain4j.StreamingAssistant;
import com.alchemist.deepexplore.agent.adapter.langchain4j.memory.LangChain4jMemoryManager;
import com.alchemist.deepexplore.agent.adapter.langchain4j.prompt.PromptContext;
import com.alchemist.deepexplore.agent.adapter.langchain4j.prompt.SystemPromptRenderer;
import com.alchemist.deepexplore.agent.adapter.langchain4j.tool.CompositeToolProvider;
import com.alchemist.deepexplore.agent.application.AgentInvocationContextRegistry;
import com.alchemist.deepexplore.agent.domain.AgentProfile;
import com.alchemist.deepexplore.agent.domain.AgentProfileDefinition;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiModelConfig {

    @Bean("fastProfileDefinition")
    AgentProfileDefinition fastProfileDefinition(AiModelProperties properties) {
        return new AgentProfileDefinition(
                AgentProfile.FAST.id(),
                "Fast",
                properties.modelName(),
                properties.maxCompletionTokens(),
                properties.reasoningEffort(),
                properties.fastThinking(),
                "classpath:prompts/assistant/fast.md",
                "execution_profile"
        );
    }

    @Bean("deepProfileDefinition")
    AgentProfileDefinition deepProfileDefinition(AiModelProperties properties) {
        return new AgentProfileDefinition(
                AgentProfile.DEEP.id(),
                "Deep",
                properties.resolvedDeepModelName(),
                properties.deepMaxCompletionTokens(),
                properties.deepReasoningEffort(),
                properties.deepThinking(),
                "classpath:prompts/assistant/deep.md",
                "execution_profile"
        );
    }

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
            CompositeToolProvider toolProvider,
            AgentInvocationContextRegistry invocationContexts,
            @Value("${ai.agent.max-tool-calling-round-trips:3}")
            int maxToolCallingRoundTrips
    ) {
        return buildAssistant(
                model,
                chatMemoryManager,
                systemPromptRenderer,
                toolProvider,
                invocationContexts,
                maxToolCallingRoundTrips,
                AgentProfile.FAST.id()
        );
    }

    @Bean("deepStreamingAssistant")
    StreamingAssistant deepStreamingAssistant(
            @Qualifier("deepStreamingChatModel") StreamingChatModel model,
            LangChain4jMemoryManager chatMemoryManager,
            SystemPromptRenderer systemPromptRenderer,
            CompositeToolProvider toolProvider,
            AgentInvocationContextRegistry invocationContexts,
            @Value("${ai.agent.max-tool-calling-round-trips:3}")
            int maxToolCallingRoundTrips
    ) {
        return buildAssistant(
                model,
                chatMemoryManager,
                systemPromptRenderer,
                toolProvider,
                invocationContexts,
                maxToolCallingRoundTrips,
                AgentProfile.DEEP.id()
        );
    }

    @Bean
    AgentProfileRuntime fastProfileRuntime(
            @Qualifier("fastStreamingAssistant") StreamingAssistant assistant,
            @Qualifier("fastProfileDefinition") AgentProfileDefinition profile
    ) {
        return new AgentProfileRuntime(
                profile.id(),
                profile.modelName(),
                assistant
        );
    }

    @Bean
    AgentProfileRuntime deepProfileRuntime(
            @Qualifier("deepStreamingAssistant") StreamingAssistant assistant,
            @Qualifier("deepProfileDefinition") AgentProfileDefinition profile
    ) {
        return new AgentProfileRuntime(
                profile.id(),
                profile.modelName(),
                assistant
        );
    }

    private StreamingAssistant buildAssistant(
            StreamingChatModel model,
            LangChain4jMemoryManager chatMemoryManager,
            SystemPromptRenderer systemPromptRenderer,
            CompositeToolProvider toolProvider,
            AgentInvocationContextRegistry invocationContexts,
            int maxToolCallingRoundTrips,
            String profileId
    ) {
        AiServices<StreamingAssistant> builder = AiServices.builder(
                        StreamingAssistant.class
                )
                .streamingChatModel(model)
                .chatMemoryProvider(chatMemoryManager::create)
                .systemMessageProviderWithContext(context ->
                        systemPromptRenderer.render(
                                profileId,
                                new PromptContext(
                                        invocationContexts.isBound(
                                                context.chatMemoryId()
                                        )
                                )
                        ))
                .maxToolCallingRoundTrips(maxToolCallingRoundTrips)
                .toolProvider(toolProvider);
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
