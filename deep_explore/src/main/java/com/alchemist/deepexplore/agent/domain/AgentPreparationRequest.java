package com.alchemist.deepexplore.agent.domain;

import java.util.List;

public record AgentPreparationRequest(
        String conversationId,
        boolean rebuildMemory,
        String sourceHeadMessageId,
        List<AgentMessage> history
) {

    public AgentPreparationRequest(
            String conversationId,
            boolean rebuildMemory,
            List<AgentMessage> history
    ) {
        this(conversationId, rebuildMemory, null, history);
    }

    public AgentPreparationRequest {
        history = history == null ? List.of() : List.copyOf(history);
    }
}
