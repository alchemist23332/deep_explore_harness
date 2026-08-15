package com.alchemist.deepexplore.harness.domain;

import java.time.Instant;

public record RunEventEnvelope(
        String eventId,
        String runId,
        String conversationId,
        String agentId,
        long sequence,
        Instant occurredAt,
        RunEvent event
) {
}
