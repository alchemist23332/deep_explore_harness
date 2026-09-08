package com.alchemist.deepexplore.agent.adapter.langchain4j.memory;

import static org.assertj.core.api.Assertions.assertThat;

import com.alchemist.deepexplore.agent.application.AgentInvocationContextRegistry;
import com.alchemist.deepexplore.agent.domain.AgentMessage;
import com.alchemist.deepexplore.agent.domain.AgentPreparationRequest;
import com.alchemist.deepexplore.agent.domain.AgentStateSnapshot;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LangChain4jMemoryManagerTest {

    private final InMemoryStore memoryStore = new InMemoryStore();
    private final AgentInvocationContextRegistry invocationContexts =
            new AgentInvocationContextRegistry();
    private final LangChain4jMemoryManager manager =
            new LangChain4jMemoryManager(
                    memoryStore,
                    invocationContexts,
                    3
            );

    @Test
    void hydratesAndTrimsMemoryFromPersistentHistory() {
        manager.prepare(new AgentPreparationRequest(
                "conversation-1",
                true,
                List.of(
                        new AgentMessage(AgentMessage.Role.USER, "one"),
                        new AgentMessage(AgentMessage.Role.ASSISTANT, "two"),
                        new AgentMessage(AgentMessage.Role.USER, "three"),
                        new AgentMessage(AgentMessage.Role.ASSISTANT, "four")
                )
        ));

        var memory = manager.create("conversation-1");

        assertThat(memory.messages())
                .extracting(LangChain4jMemoryManagerTest::text)
                .containsExactly("two", "three", "four");
    }

    @Test
    void mapsRunScopedMemoryIdToPersistentConversation() {
        memoryStore.updateMessages(
                "conversation-1",
                List.of(UserMessage.from("conversation history"))
        );
        invocationContexts.bind("run-1", "conversation-1", null);

        ChatMemory memory = manager.create("run-1");

        assertThat(memory.id()).isEqualTo("conversation-1");
        assertThat(memory.messages())
                .extracting(LangChain4jMemoryManagerTest::text)
                .containsExactly("conversation history");
    }

    @Test
    void restoresOpaqueSnapshotAfterFailedTurn() {
        memoryStore.updateMessages(
                "conversation-1",
                List.of(UserMessage.from("stable"))
        );
        AgentStateSnapshot snapshot = manager.prepare(new AgentPreparationRequest(
                "conversation-1",
                false,
                List.of()
        ));
        memoryStore.updateMessages(
                "conversation-1",
                List.of(UserMessage.from("stable"), UserMessage.from("failed"))
        );

        manager.restore("conversation-1", snapshot);

        assertThat(memoryStore.getMessages("conversation-1"))
                .extracting(LangChain4jMemoryManagerTest::text)
                .containsExactly("stable");
    }

    @Test
    void rebuildsDirtyMemoryFromCanonicalHistory() {
        memoryStore.updateMessages(
                "conversation-1",
                List.of(UserMessage.from("old"))
        );
        manager.invalidate("conversation-1");
        memoryStore.updateMessages(
                "conversation-1",
                List.of(UserMessage.from("late stale write"))
        );

        manager.prepare(new AgentPreparationRequest(
                "conversation-1",
                false,
                "cancelled-message-id",
                List.of(
                        new AgentMessage(AgentMessage.Role.USER, "old"),
                        new AgentMessage(AgentMessage.Role.USER, "cancelled turn")
                )
        ));

        assertThat(memoryStore.getMessages("conversation-1"))
                .extracting(LangChain4jMemoryManagerTest::text)
                .containsExactly("old", "cancelled turn");
        assertThat(memoryStore.isDirty("conversation-1")).isFalse();
        assertThat(memoryStore.sourceHeadMessageId("conversation-1"))
                .isEqualTo("cancelled-message-id");
    }

    @Test
    void persistsThinkingOnlyForToolCallMessages() {
        AiMessage finalAnswer = AiMessage.builder()
                .text("final")
                .thinking("private final reasoning")
                .build();
        AiMessage toolCall = AiMessage.builder()
                .thinking("reasoning required by the provider")
                .toolExecutionRequests(List.of(
                        ToolExecutionRequest.builder()
                                .id("tool-1")
                                .name("web_search")
                                .arguments("{\"query\":\"latest\"}")
                                .build()
                ))
                .build();

        List<ChatMessage> persisted =
                PostgresChatMemoryStore.messagesForPersistence(
                        List.of(finalAnswer, toolCall)
                );

        assertThat(((AiMessage) persisted.get(0)).thinking()).isNull();
        assertThat(((AiMessage) persisted.get(1)).thinking())
                .isEqualTo("reasoning required by the provider");
    }

    private static String text(ChatMessage message) {
        if (message instanceof UserMessage userMessage) {
            return userMessage.singleText();
        }
        return ((AiMessage) message).text();
    }

    private static final class InMemoryStore implements PersistentChatMemoryStore {

        private final Map<Object, List<ChatMessage>> values = new HashMap<>();
        private final Set<Object> dirty = new HashSet<>();
        private final Map<Object, String> sourceHeads = new HashMap<>();

        @Override
        public boolean contains(Object memoryId) {
            return values.containsKey(memoryId);
        }

        @Override
        public boolean isDirty(Object memoryId) {
            return dirty.contains(memoryId);
        }

        @Override
        public void markDirty(Object memoryId) {
            dirty.add(memoryId);
        }

        @Override
        public void clearDirty(Object memoryId) {
            dirty.remove(memoryId);
        }

        @Override
        public String sourceHeadMessageId(Object memoryId) {
            return sourceHeads.get(memoryId);
        }

        @Override
        public void markSynchronized(
                Object memoryId,
                String sourceHeadMessageId
        ) {
            if (sourceHeadMessageId == null) {
                sourceHeads.remove(memoryId);
            } else {
                sourceHeads.put(memoryId, sourceHeadMessageId);
            }
            dirty.remove(memoryId);
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
