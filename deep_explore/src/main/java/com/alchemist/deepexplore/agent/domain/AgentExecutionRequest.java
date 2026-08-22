package com.alchemist.deepexplore.agent.domain;

public record AgentExecutionRequest(
        String runId,
        String conversationId,
        String agentId,
        String profileId,
        String message,
        WebSearchProvider searchProvider
) {

    public AgentExecutionRequest {
        searchProvider = searchProvider == null
                ? WebSearchProvider.TAVILY
                : searchProvider;
    }

    public AgentExecutionRequest(
            String runId,
            String conversationId,
            String agentId,
            String profileId,
            String message
    ) {
        this(
                runId,
                conversationId,
                agentId,
                profileId,
                message,
                WebSearchProvider.TAVILY
        );
    }
}
