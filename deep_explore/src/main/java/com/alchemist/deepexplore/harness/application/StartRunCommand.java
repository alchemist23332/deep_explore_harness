package com.alchemist.deepexplore.harness.application;

public record StartRunCommand(
        String conversationId,
        String message,
        String agentId,
        String profileId,
        String userMessageId,
        String userParentMessageId,
        String assistantMessageId
) {
}
