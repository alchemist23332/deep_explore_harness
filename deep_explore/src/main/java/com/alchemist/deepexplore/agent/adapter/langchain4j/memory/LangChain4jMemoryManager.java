package com.alchemist.deepexplore.agent.adapter.langchain4j.memory;

import static dev.langchain4j.data.message.ChatMessageDeserializer.messagesFromJson;
import static dev.langchain4j.data.message.ChatMessageSerializer.messagesToJson;

import com.alchemist.deepexplore.agent.domain.AgentStateSnapshot;
import com.alchemist.deepexplore.conversation.domain.Conversation;
import com.alchemist.deepexplore.conversation.domain.ConversationMessage;
import com.alchemist.deepexplore.conversation.port.ConversationStore;
import com.alchemist.deepexplore.conversation.port.MessageStore;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class LangChain4jMemoryManager {

    private final PersistentChatMemoryStore memoryStore;
    private final ConversationStore conversationStore;
    private final MessageStore messageStore;
    private final int maxMessages;

    public LangChain4jMemoryManager(
            PersistentChatMemoryStore memoryStore,
            ConversationStore conversationStore,
            MessageStore messageStore,
            @Value("${app.conversation.max-messages:20}") int maxMessages
    ) {
        this.memoryStore = memoryStore;
        this.conversationStore = conversationStore;
        this.messageStore = messageStore;
        this.maxMessages = maxMessages;
    }

    public ChatMemory create(Object memoryId) {
        String conversationId = memoryId.toString();
        ChatMemory memory = MessageWindowChatMemory.builder()
                .id(conversationId)
                .maxMessages(maxMessages)
                .chatMemoryStore(memoryStore)
                .alwaysKeepSystemMessageFirst(true)
                .build();
        if (!memoryStore.contains(conversationId)) {
            memory.set(historyMessages(conversationId));
        }
        return memory;
    }

    public AgentStateSnapshot prepare(
            String conversationId,
            boolean replay,
            String rewindHeadMessageId
    ) {
        if (replay) {
            rebuildThrough(conversationId, rewindHeadMessageId);
        }
        return new AgentStateSnapshot(messagesToJson(
                List.copyOf(create(conversationId).messages())
        ));
    }

    public void restore(String conversationId, AgentStateSnapshot snapshot) {
        memoryStore.updateMessages(
                conversationId,
                messagesFromJson(snapshot.payload())
        );
    }

    private void rebuildThrough(String conversationId, String headMessageId) {
        memoryStore.updateMessages(
                conversationId,
                toChatMessages(messageStore.branch(
                        conversationId,
                        headMessageId,
                        maxMessages
                ))
        );
    }

    private List<ChatMessage> historyMessages(String conversationId) {
        String headMessageId = conversationStore.find(conversationId)
                .map(Conversation::headMessageId)
                .orElse(null);
        return toChatMessages(messageStore.branch(
                conversationId,
                headMessageId,
                maxMessages
        ));
    }

    private static List<ChatMessage> toChatMessages(
            List<ConversationMessage> messages
    ) {
        return messages.stream()
                .map(message -> message.role() == ConversationMessage.Role.USER
                        ? UserMessage.from(message.content())
                        : AiMessage.from(message.content()))
                .map(ChatMessage.class::cast)
                .toList();
    }
}
