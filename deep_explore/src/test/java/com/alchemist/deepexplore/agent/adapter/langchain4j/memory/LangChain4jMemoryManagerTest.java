package com.alchemist.deepexplore.agent.adapter.langchain4j.memory;

import static org.assertj.core.api.Assertions.assertThat;

import com.alchemist.deepexplore.agent.domain.AgentMessage;
import com.alchemist.deepexplore.agent.domain.AgentPreparationRequest;
import com.alchemist.deepexplore.agent.domain.AgentStateSnapshot;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LangChain4jMemoryManagerTest {

    private final InMemoryStore memoryStore = new InMemoryStore();
    private final LangChain4jMemoryManager manager =
            new LangChain4jMemoryManager(memoryStore, 3);

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
