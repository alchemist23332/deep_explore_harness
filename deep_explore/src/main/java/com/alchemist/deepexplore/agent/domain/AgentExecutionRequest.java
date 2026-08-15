package com.alchemist.deepexplore.agent.domain;

public record AgentExecutionRequest(
        String runId,
        String conversationId,
        String agentId,
        String profileId,
        String message
) {
}
