package com.alchemist.deepexplore.api;

import com.alchemist.deepexplore.agent.AgentMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatRequest(
        @Size(max = 100) String conversationId,
        @NotBlank @Size(max = 20_000) String message,
        AgentMode mode,
        @Size(max = 100) String userMessageId,
        @Size(max = 100) String userParentMessageId,
        @Size(max = 100) String assistantMessageId
) {

    public ChatRequest(String conversationId, String message) {
        this(conversationId, message, AgentMode.FAST, null, null, null);
    }

    public ChatRequest(String conversationId, String message, AgentMode mode) {
        this(conversationId, message, mode, null, null, null);
    }

    public ChatRequest {
        mode = mode == null ? AgentMode.FAST : mode;
    }
}
