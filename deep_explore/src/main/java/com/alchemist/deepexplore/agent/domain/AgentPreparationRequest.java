package com.alchemist.deepexplore.agent.domain;

import java.util.List;

public record AgentPreparationRequest(
        String conversationId,
        boolean rebuildMemory,
        List<AgentMessage> history
) {

    public AgentPreparationRequest {
        history = history == null ? List.of() : List.copyOf(history);
    }
}
