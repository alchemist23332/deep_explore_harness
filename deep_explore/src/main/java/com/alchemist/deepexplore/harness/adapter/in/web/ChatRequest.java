package com.alchemist.deepexplore.harness.adapter.in.web;

import com.alchemist.deepexplore.agent.domain.AgentProfile;
import com.alchemist.deepexplore.agent.domain.WebSearchProvider;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatRequest(
        @Size(max = 100) String conversationId,
        @NotBlank @Size(max = 20_000) String message,
        AgentProfile mode,
        @Size(max = 100) String userMessageId,
        @Size(max = 100) String userParentMessageId,
        @Size(max = 100) String assistantMessageId,
        WebSearchProvider searchProvider
) {

    public ChatRequest(String conversationId, String message) {
        this(conversationId, message, AgentProfile.FAST, null, null, null, null);
    }

    public ChatRequest {
        mode = mode == null ? AgentProfile.FAST : mode;
    }
}
