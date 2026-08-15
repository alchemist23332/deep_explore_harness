package com.alchemist.deepexplore.conversation;

import static org.assertj.core.api.Assertions.assertThat;

import com.alchemist.deepexplore.agent.adapter.langchain4j.memory.PostgresChatMemoryStore;
import com.alchemist.deepexplore.conversation.adapter.out.postgres.PostgresConversationLock;
import com.alchemist.deepexplore.conversation.adapter.out.postgres.PostgresConversationStore;
import com.alchemist.deepexplore.conversation.adapter.out.postgres.PostgresMessageStore;
import com.alchemist.deepexplore.conversation.domain.Conversation;
import com.alchemist.deepexplore.conversation.domain.ConversationMessage;
import com.alchemist.deepexplore.harness.adapter.out.postgres.PostgresCheckpointStore;
import com.alchemist.deepexplore.harness.adapter.out.postgres.PostgresRunEventStore;
import com.alchemist.deepexplore.harness.adapter.out.postgres.PostgresRunStore;
import com.alchemist.deepexplore.harness.domain.AgentRun;
import com.alchemist.deepexplore.harness.domain.RunEvent;
import com.alchemist.deepexplore.harness.domain.RunEventEnvelope;
import com.alchemist.deepexplore.harness.domain.RunStatus;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class ConversationPersistenceIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    PostgresConversationStore conversationStore;

    @Autowired
    PostgresMessageStore messageStore;

    @Autowired
    PostgresConversationLock conversationLock;

    @Autowired
    PostgresChatMemoryStore memoryStore;

    @Autowired
    PostgresRunStore runStore;

    @Autowired
    PostgresRunEventStore eventStore;

    @Autowired
    PostgresCheckpointStore checkpointStore;

    @Test
    void persistsConversationHarnessAndMemoryState() {
        Conversation conversation =
                conversationStore.create("conversation-1", "Persistent chat");

        assertThat(conversationLock.tryAcquire(
                conversation.id(),
                Duration.ofMinutes(5)
        )).isTrue();
        assertThat(conversationLock.tryAcquire(
                conversation.id(),
                Duration.ofMinutes(5)
        )).isFalse();

        messageStore.append(
                conversation.id(),
                "user-1",
                null,
                ConversationMessage.Role.USER,
                "hello",
                ConversationMessage.Status.COMPLETE,
                null,
                null
        );
        messageStore.append(
                conversation.id(),
                "assistant-1",
                "user-1",
                ConversationMessage.Role.ASSISTANT,
                "hi",
                ConversationMessage.Status.COMPLETE,
                "test-model",
                12
        );
        memoryStore.updateMessages(
                conversation.id(),
                List.of(UserMessage.from("hello"), AiMessage.from("hi"))
        );
        conversationLock.release(conversation.id());

        Instant now = Instant.now();
        AgentRun run = runStore.create(new AgentRun(
                "run-1",
                conversation.id(),
                "user-1",
                "assistant-1",
                "assistant",
                "fast",
                RunStatus.RUNNING,
                null,
                null,
                now,
                now,
                null
        ));
        eventStore.append(new RunEventEnvelope(
                "event-1",
                run.id(),
                conversation.id(),
                run.agentId(),
                1,
                now,
                new RunEvent.RunStarted("fast", "user-1")
        ));
        checkpointStore.save(run.id(), "{\"state\":\"ready\"}");
        runStore.complete(run.id());

        assertThat(messageStore.list(conversation.id()))
                .extracting(ConversationMessage::content)
                .containsExactly("hello", "hi");
        assertThat(memoryStore.getMessages(conversation.id())).hasSize(2);
        assertThat(eventStore.list(run.id(), 0))
                .singleElement()
                .extracting(RunEventEnvelope::sequence)
                .isEqualTo(1L);
        assertThat(checkpointStore.findLatest(run.id()))
                .get()
                .extracting(RunCheckpoint -> RunCheckpoint.version())
                .isEqualTo(1L);
        assertThat(runStore.find(run.id()))
                .get()
                .extracting(AgentRun::status)
                .isEqualTo(RunStatus.COMPLETED);
    }
}
