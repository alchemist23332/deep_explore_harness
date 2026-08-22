package com.alchemist.deepexplore.workspace.domain;

public record CommandResult(
        String command,
        Integer exitCode,
        String stdout,
        String stderr,
        long durationMs,
        boolean timedOut,
        boolean truncated
) {
}
