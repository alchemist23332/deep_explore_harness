package com.alchemist.deepexplore.harness.adapter.in.web;

import com.alchemist.deepexplore.agent.domain.AgentProfile;
import com.alchemist.deepexplore.agent.domain.WebSearchProvider;
import com.alchemist.deepexplore.harness.application.command.ChatCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatRequest(
        @Size(max = 100) String conversationId,
        @NotBlank @Size(max = 20_000) String message,
        AgentProfile mode,
        @Size(max = 100) String userMessageId,
        @Size(max = 100) String userParentMessageId,
        @Size(max = 100) String assistantMessageId,
        WebSearchProvider searchProvider,
        @Size(max = 100) String workspaceId,
        @Size(max = 100) String agentId,
        @Size(max = 100) String profileId
) {

    public ChatRequest(String conversationId, String message) {
        this(
                conversationId,
                message,
                AgentProfile.FAST,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    public ChatRequest(
            String conversationId,
            String message,
            AgentProfile mode,
            String userMessageId,
            String userParentMessageId,
            String assistantMessageId,
            WebSearchProvider searchProvider
    ) {
        this(
                conversationId,
                message,
                mode,
                userMessageId,
                userParentMessageId,
                assistantMessageId,
                searchProvider,
                null,
                null,
                null
        );
    }

    public ChatRequest(
            String conversationId,
            String message,
            AgentProfile mode,
            String userMessageId,
            String userParentMessageId,
            String assistantMessageId,
            WebSearchProvider searchProvider,
            String workspaceId
    ) {
        this(
                conversationId,
                message,
                mode,
                userMessageId,
                userParentMessageId,
                assistantMessageId,
                searchProvider,
                workspaceId,
                null,
                null
        );
    }

    public ChatRequest(
            String conversationId,
            String message,
            AgentProfile mode,
            String userMessageId,
            String userParentMessageId,
            String assistantMessageId,
            WebSearchProvider searchProvider,
            String workspaceId,
            String agentId
    ) {
        this(
                conversationId,
                message,
                mode,
                userMessageId,
                userParentMessageId,
                assistantMessageId,
                searchProvider,
                workspaceId,
                agentId,
                null
        );
    }

    public ChatRequest {
        mode = mode == null ? AgentProfile.FAST : mode;
    }

    ChatCommand toCommand() {
        return new ChatCommand(
                conversationId,
                message,
                mode,
                userMessageId,
                userParentMessageId,
                assistantMessageId,
                searchProvider,
                workspaceId,
                agentId,
                profileId
        );
    }
}
