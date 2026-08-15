package com.alchemist.deepexplore.conversation.port;

import com.alchemist.deepexplore.conversation.domain.ConversationMessage;
import java.util.List;

public interface MessageStore {

    boolean append(
            String conversationId,
            String messageId,
            String parentMessageId,
            ConversationMessage.Role role,
            String content,
            ConversationMessage.Status status,
            String model,
            Integer tokenUsage
    );

    List<ConversationMessage> list(String conversationId);

    boolean exists(String conversationId, String messageId);

    List<ConversationMessage> branch(
            String conversationId,
            String headMessageId,
            int limit
    );
}
