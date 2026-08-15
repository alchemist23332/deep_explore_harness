package com.alchemist.deepexplore.conversation.domain;

import java.time.Instant;

public record ConversationMessage(
        String id,
        String conversationId,
        String parentMessageId,
        long sequence,
        Role role,
        String content,
        Status status,
        String model,
        Integer tokenUsage,
        Instant createdAt
) {

    public enum Role {
        USER,
        ASSISTANT
    }

    public enum Status {
        COMPLETE,
        INCOMPLETE
    }
}
