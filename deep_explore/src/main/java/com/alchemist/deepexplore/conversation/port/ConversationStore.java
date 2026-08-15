package com.alchemist.deepexplore.conversation.port;

import com.alchemist.deepexplore.conversation.domain.Conversation;
import java.util.List;
import java.util.Optional;

public interface ConversationStore {

    Conversation create(String requestedId, String title);

    Optional<Conversation> find(String conversationId);

    List<Conversation> list();

    void rename(String conversationId, String title);

    void setStatus(String conversationId, Conversation.Status status);

    void delete(String conversationId);
}
