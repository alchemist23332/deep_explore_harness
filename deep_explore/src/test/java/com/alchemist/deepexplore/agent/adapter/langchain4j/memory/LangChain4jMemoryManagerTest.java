package com.alchemist.deepexplore.agent.adapter.langchain4j.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.alchemist.deepexplore.agent.domain.AgentStateSnapshot;
import com.alchemist.deepexplore.conversation.domain.Conversation;
import com.alchemist.deepexplore.conversation.domain.ConversationMessage;
import com.alchemist.deepexplore.conversation.port.ConversationStore;
import com.alchemist.deepexplore.conversation.port.MessageStore;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LangChain4jMemoryManagerTest {

    private final InMemoryStore memoryStore = new InMemoryStore();
    private final ConversationStore conversationStore = mock(ConversationStore.class);
    private final MessageStore messageStore = mock(MessageStore.class);
    private final LangChain4jMemoryManager manager =
            new LangChain4jMemoryManager(
                    memoryStore,
                    conversationStore,
                    messageStore,
                    3
            );

    @Test
    void hydratesAndTrimsMemoryFromPersistentHistory() {
        when(conversationStore.find("conversation-1"))
                .thenReturn(Optional.of(new Conversation(
                        "conversation-1",
                        "test",
                        Conversation.Status.REGULAR,
                        "message-4",
                        Instant.EPOCH,
                        Instant.EPOCH
                )));
        when(messageStore.branch("conversation-1", "message-4", 3))
                .thenReturn(List.of(
                        message(1, ConversationMessage.Role.USER, "one"),
                        message(2, ConversationMessage.Role.ASSISTANT, "two"),
                        message(3, ConversationMessage.Role.USER, "three"),
                        message(4, ConversationMessage.Role.ASSISTANT, "four")
                ));

        var memory = manager.create("conversation-1");

        assertThat(memory.messages())
                .extracting(LangChain4jMemoryManagerTest::text)
                .containsExactly("two", "three", "four");
    }

    @Test
    void restoresOpaqueSnapshotAfterFailedTurn() {
        memoryStore.updateMessages(
                "conversation-1",
                List.of(UserMessage.from("stable"))
        );
        AgentStateSnapshot snapshot = manager.prepare(
                "conversation-1",
                false,
                null
        );
        memoryStore.updateMessages(
                "conversation-1",
                List.of(UserMessage.from("stable"), UserMessage.from("failed"))
        );

        manager.restore("conversation-1", snapshot);

        assertThat(memoryStore.getMessages("conversation-1"))
                .extracting(LangChain4jMemoryManagerTest::text)
                .containsExactly("stable");
    }

    private static ConversationMessage message(
            long sequence,
            ConversationMessage.Role role,
            String content
    ) {
        return new ConversationMessage(
                "message-" + sequence,
                "conversation-1",
                null,
                sequence,
                role,
                content,
                ConversationMessage.Status.COMPLETE,
                null,
                null,
                Instant.EPOCH.plusSeconds(sequence)
        );
    }

    private static String text(ChatMessage message) {
        if (message instanceof UserMessage userMessage) {
            return userMessage.singleText();
        }
        return ((dev.langchain4j.data.message.AiMessage) message).text();
    }

    private static final class InMemoryStore implements PersistentChatMemoryStore {

        private final Map<Object, List<ChatMessage>> values = new HashMap<>();

        @Override
        public boolean contains(Object memoryId) {
            return values.containsKey(memoryId);
        }

        @Override
        public List<ChatMessage> getMessages(Object memoryId) {
            return new ArrayList<>(values.getOrDefault(memoryId, List.of()));
        }

        @Override
        public void updateMessages(Object memoryId, List<ChatMessage> messages) {
            values.put(memoryId, List.copyOf(messages));
        }

        @Override
        public void deleteMessages(Object memoryId) {
            values.remove(memoryId);
        }
    }
}
