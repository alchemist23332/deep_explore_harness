package com.alchemist.deepexplore.harness.application.command;

import com.alchemist.deepexplore.agent.domain.AgentProfile;
import com.alchemist.deepexplore.agent.domain.WebSearchProvider;

public record ChatCommand(
        String conversationId,
        String message,
        AgentProfile profile,
        String userMessageId,
        String userParentMessageId,
        String assistantMessageId,
        WebSearchProvider searchProvider,
        String workspaceId
) {

    public ChatCommand {
        profile = profile == null ? AgentProfile.FAST : profile;
    }

    public ChatCommand(
            String conversationId,
            String message,
            AgentProfile profile,
            String userMessageId,
            String userParentMessageId,
            String assistantMessageId,
            WebSearchProvider searchProvider
    ) {
        this(
                conversationId,
                message,
                profile,
                userMessageId,
                userParentMessageId,
                assistantMessageId,
                searchProvider,
                null
        );
    }
}
