package com.alchemist.deepexplore.harness.application;

import com.alchemist.deepexplore.agent.domain.WebSearchProvider;

public record StartRunCommand(
        String conversationId,
        String message,
        String agentId,
        String profileId,
        String userMessageId,
        String userParentMessageId,
        String assistantMessageId,
        WebSearchProvider searchProvider
) {

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
                null
        );
    }
}
