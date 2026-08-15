package com.alchemist.deepexplore.conversation.domain;

import java.time.Instant;

public record Conversation(
        String id,
        String title,
        Status status,
        String headMessageId,
        Instant createdAt,
        Instant updatedAt
) {

    public enum Status {
        REGULAR,
        ARCHIVED
    }
}
