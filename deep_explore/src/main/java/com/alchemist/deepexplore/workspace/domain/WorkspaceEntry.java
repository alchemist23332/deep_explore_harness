package com.alchemist.deepexplore.workspace.domain;

import java.time.Instant;

public record WorkspaceEntry(
        String name,
        String path,
        Type type,
        long size,
        Instant modifiedAt
) {

    public enum Type {
        FILE,
        DIRECTORY
    }
}
