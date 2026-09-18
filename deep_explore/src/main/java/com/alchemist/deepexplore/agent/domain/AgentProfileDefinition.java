package com.alchemist.deepexplore.agent.domain;

public record AgentProfileDefinition(
        String id,
        String displayName,
        String modelName,
        int maxCompletionTokens,
        String reasoningEffort,
        String thinking,
        String promptResource,
        String promptRootElement
) {
}
