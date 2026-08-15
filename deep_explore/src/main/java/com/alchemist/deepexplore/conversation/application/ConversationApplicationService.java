package com.alchemist.deepexplore.conversation.application;

import com.alchemist.deepexplore.conversation.domain.Conversation;
import com.alchemist.deepexplore.conversation.domain.ConversationMessage;
import com.alchemist.deepexplore.conversation.port.ConversationStore;
import com.alchemist.deepexplore.conversation.port.MessageStore;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConversationApplicationService {

    private final ConversationStore conversationStore;
    private final MessageStore messageStore;

    public ConversationApplicationService(
            ConversationStore conversationStore,
            MessageStore messageStore
    ) {
        this.conversationStore = conversationStore;
        this.messageStore = messageStore;
    }

    public List<Conversation> list() {
        return conversationStore.list();
    }

    public Conversation create() {
        return conversationStore.create(null, null);
    }

    public Conversation get(String conversationId) {
        return requireConversation(conversationId);
    }

    @Transactional
    public Conversation update(
            String conversationId,
            String title,
            Conversation.Status status
    ) {
        requireConversation(conversationId);
        if (title != null) {
            conversationStore.rename(conversationId, title);
        }
        if (status != null) {
            conversationStore.setStatus(conversationId, status);
        }
        return requireConversation(conversationId);
    }

    public void delete(String conversationId) {
        conversationStore.delete(conversationId);
    }

    public List<ConversationMessage> messages(String conversationId) {
        requireConversation(conversationId);
        return messageStore.list(conversationId);
    }

    @Transactional
    public void importHistory(List<ConversationImport> imports) {
        for (ConversationImport item : imports) {
            conversationStore.create(item.id(), item.title());
            if (item.status() != null) {
                conversationStore.setStatus(item.id(), item.status());
            }
            item.messages().stream()
                    .sorted(Comparator.comparing(ConversationImport.Message::createdAt))
                    .forEach(message -> messageStore.append(
                            item.id(),
                            message.id(),
                            message.parentMessageId(),
                            message.role(),
                            message.content(),
                            message.status() == null
                                    ? ConversationMessage.Status.COMPLETE
                                    : message.status(),
                            null,
                            null
                    ));
        }
    }

    private Conversation requireConversation(String conversationId) {
        return conversationStore.find(conversationId)
                .orElseThrow(() -> new ConversationNotFoundException(conversationId));
    }
}
