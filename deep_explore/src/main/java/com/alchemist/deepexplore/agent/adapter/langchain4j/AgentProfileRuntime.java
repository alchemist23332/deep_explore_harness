package com.alchemist.deepexplore.agent.adapter.langchain4j;

public record AgentProfileRuntime(
        String profileId,
        String modelName,
        StreamingAssistant assistant
) {
}
