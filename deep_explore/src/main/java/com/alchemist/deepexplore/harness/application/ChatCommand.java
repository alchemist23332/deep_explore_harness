package com.alchemist.deepexplore.harness.application;

import com.alchemist.deepexplore.agent.domain.AgentProfile;
import com.alchemist.deepexplore.agent.domain.WebSearchProvider;

public record ChatCommand(
        String conversationId,
        String message,
        AgentProfile profile,
        String userMessageId,
        String userParentMessageId,
        String assistantMessageId,
        WebSearchProvider searchProvider
) {

    public ChatCommand {
        profile = profile == null ? AgentProfile.FAST : profile;
    }
}
