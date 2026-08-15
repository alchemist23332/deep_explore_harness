package com.alchemist.deepexplore.harness.domain;

import java.time.Instant;

public record RunCheckpoint(
        String runId,
        long version,
        String stateJson,
        Instant createdAt
) {
}
