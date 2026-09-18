package com.alchemist.deepexplore.harness.domain;

import java.time.Instant;

public record AgentRun(
        String id,
        String conversationId,
        String workspaceId,
        String userMessageId,
        String assistantMessageId,
        String agentId,
        String profileId,
        RunStatus status,
        String errorCode,
        String errorMessage,
        Instant createdAt,
        Instant startedAt,
        Instant completedAt,
        long version
) {

    public AgentRun(
            String id,
            String conversationId,
            String workspaceId,
            String userMessageId,
            String assistantMessageId,
            String agentId,
            String profileId,
            RunStatus status,
            String errorCode,
            String errorMessage,
            Instant createdAt,
            Instant startedAt,
            Instant completedAt
    ) {
        this(
                id,
                conversationId,
                workspaceId,
                userMessageId,
                assistantMessageId,
                agentId,
                profileId,
                status,
                errorCode,
                errorMessage,
                createdAt,
                startedAt,
                completedAt,
                0
        );
    }

    public AgentRun(
            String id,
            String conversationId,
            String userMessageId,
            String assistantMessageId,
            String agentId,
            String profileId,
            RunStatus status,
            String errorCode,
            String errorMessage,
            Instant createdAt,
            Instant startedAt,
            Instant completedAt
    ) {
        this(
                id,
                conversationId,
                null,
                userMessageId,
                assistantMessageId,
                agentId,
                profileId,
                status,
                errorCode,
                errorMessage,
                createdAt,
                startedAt,
                completedAt,
                0
        );
    }
}
