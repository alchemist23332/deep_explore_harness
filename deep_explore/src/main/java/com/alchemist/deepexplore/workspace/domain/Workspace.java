package com.alchemist.deepexplore.workspace.domain;

import java.time.Instant;

public record Workspace(
        String id,
        String ownerId,
        String name,
        RuntimeProfile runtimeProfile,
        String containerId,
        WorkspaceStatus status,
        String lastError,
        Instant createdAt,
        Instant updatedAt,
        Instant lastStartedAt
) {
}
