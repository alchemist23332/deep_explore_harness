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
        String workspaceId,
        String agentId,
        String profileId
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
                null,
                null,
                null
        );
    }

    public ChatCommand(
            String conversationId,
            String message,
            AgentProfile profile,
            String userMessageId,
            String userParentMessageId,
            String assistantMessageId,
            WebSearchProvider searchProvider,
            String workspaceId
    ) {
        this(
                conversationId,
                message,
                profile,
                userMessageId,
                userParentMessageId,
                assistantMessageId,
                searchProvider,
                workspaceId,
                null,
                null
        );
    }

    public ChatCommand(
            String conversationId,
            String message,
            AgentProfile profile,
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
                profile,
                userMessageId,
                userParentMessageId,
                assistantMessageId,
                searchProvider,
                workspaceId,
                agentId,
                null
        );
    }

    public String resolvedProfileId() {
        return profileId == null || profileId.isBlank()
                ? profile.id()
                : profileId;
    }
}
