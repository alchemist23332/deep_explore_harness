package com.alchemist.deepexplore.harness.adapter.in.web;

import com.alchemist.deepexplore.harness.application.ToolStatus;
import java.time.Instant;

public record ChatStreamEvent(
        String type,
        String conversationId,
        String content,
        String runId,
        Long sequence,
        Instant occurredAt,
        String assistantMessageId,
        ToolActivity tool
) {

    public record ToolActivity(
            String toolCallId,
            String toolName,
            String displayName,
            ToolStatus status,
            String summary,
            String provider
    ) {
    }
}
