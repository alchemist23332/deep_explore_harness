package com.alchemist.deepexplore.workspace.domain;

import java.time.Instant;

public record WorkspaceFile(
        String path,
        String content,
        long size,
        Instant modifiedAt
) {
}
