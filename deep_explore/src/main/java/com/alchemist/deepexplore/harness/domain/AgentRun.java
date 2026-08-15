package com.alchemist.deepexplore.harness.domain;

import java.time.Instant;

public record AgentRun(
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
}
