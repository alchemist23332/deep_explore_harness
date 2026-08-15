package com.alchemist.deepexplore.conversation.application;

import com.alchemist.deepexplore.conversation.domain.Conversation;
import com.alchemist.deepexplore.conversation.domain.ConversationMessage;
import java.time.Instant;
import java.util.List;

public record ConversationImport(
        String id,
        String title,
        Conversation.Status status,
        List<Message> messages
) {

    public record Message(
            String id,
            String parentMessageId,
            ConversationMessage.Role role,
            String content,
            ConversationMessage.Status status,
            Instant createdAt
    ) {
    }
}
