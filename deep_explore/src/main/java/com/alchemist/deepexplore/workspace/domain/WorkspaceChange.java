package com.alchemist.deepexplore.workspace.domain;

import java.time.Instant;

public record WorkspaceChange(
        Kind kind,
        String path,
        String parentPath,
        Instant occurredAt
) {

    public enum Kind {
        CREATED,
        MODIFIED,
        DELETED,
        OVERFLOW
    }
}
