package com.alchemist.deepexplore.agent.adapter.langchain4j.memory;

import static dev.langchain4j.data.message.ChatMessageDeserializer.messagesFromJson;
import static dev.langchain4j.data.message.ChatMessageSerializer.messagesToJson;

import com.alchemist.deepexplore.agent.domain.AgentMessage;
import com.alchemist.deepexplore.agent.domain.AgentPreparationRequest;
import com.alchemist.deepexplore.agent.domain.AgentStateSnapshot;
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
    private final int maxMessages;

    public LangChain4jMemoryManager(
            PersistentChatMemoryStore memoryStore,
            @Value("${app.conversation.max-messages:20}") int maxMessages
    ) {
        this.memoryStore = memoryStore;
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
        return memory;
    }

    public AgentStateSnapshot prepare(AgentPreparationRequest request) {
        String conversationId = request.conversationId();
        if (request.rebuildMemory() || !memoryStore.contains(conversationId)) {
            memoryStore.updateMessages(
                    conversationId,
                    toChatMessages(request.history())
            );
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

    private static List<ChatMessage> toChatMessages(
            List<AgentMessage> messages
    ) {
        return messages.stream()
                .map(message -> message.role() == AgentMessage.Role.USER
                        ? UserMessage.from(message.content())
                        : AiMessage.from(message.content()))
                .map(ChatMessage.class::cast)
                .toList();
    }
}
