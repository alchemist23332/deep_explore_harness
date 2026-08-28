package com.alchemist.deepexplore.harness.application.command;

import com.alchemist.deepexplore.agent.domain.WebSearchProvider;

public record StartRunCommand(
        String conversationId,
        String message,
        String agentId,
        String profileId,
        String userMessageId,
        String userParentMessageId,
        String assistantMessageId,
        WebSearchProvider searchProvider,
        String workspaceId
) {

    public StartRunCommand(
            String conversationId,
            String message,
            String agentId,
            String profileId,
            String userMessageId,
            String userParentMessageId,
            String assistantMessageId,
            WebSearchProvider searchProvider
    ) {
        this(
                conversationId,
                message,
                agentId,
                profileId,
                userMessageId,
                userParentMessageId,
                assistantMessageId,
                searchProvider,
                null
        );
    }

    public StartRunCommand(
            String conversationId,
            String message,
            String agentId,
            String profileId,
            String userMessageId,
            String userParentMessageId,
            String assistantMessageId
    ) {
        this(
                conversationId,
                message,
                agentId,
                profileId,
                userMessageId,
                userParentMessageId,
                assistantMessageId,
                null,
                null
        );
    }
}
