package com.alchemist.deepexplore.harness.application.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.alchemist.deepexplore.agent.application.AgentExecutorRegistry;
import com.alchemist.deepexplore.agent.domain.AgentExecutionEvent;
import com.alchemist.deepexplore.agent.domain.AgentExecutionRequest;
import com.alchemist.deepexplore.agent.domain.AgentMessage;
import com.alchemist.deepexplore.agent.domain.AgentPreparationRequest;
import com.alchemist.deepexplore.agent.domain.AgentStateSnapshot;
import com.alchemist.deepexplore.agent.domain.WebSearchProvider;
import com.alchemist.deepexplore.agent.spi.AgentExecutor;
import com.alchemist.deepexplore.conversation.domain.Conversation;
import com.alchemist.deepexplore.conversation.port.ConversationLock;
import com.alchemist.deepexplore.conversation.port.ConversationStore;
import com.alchemist.deepexplore.conversation.port.MessageStore;
import com.alchemist.deepexplore.harness.application.command.StartRunCommand;
import com.alchemist.deepexplore.harness.domain.RunCheckpoint;
import com.alchemist.deepexplore.harness.domain.RunEvent;
import com.alchemist.deepexplore.harness.port.CheckpointStore;
import com.alchemist.deepexplore.harness.port.RunEventStore;
import com.alchemist.deepexplore.harness.port.RunStore;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

class HarnessOrchestratorTest {

    private final ConversationStore conversationStore = mock(ConversationStore.class);
    private final MessageStore messageStore = mock(MessageStore.class);
    private final ConversationLock conversationLock = mock(ConversationLock.class);
    private final RunStore runStore = mock(RunStore.class);
    private final RunEventStore eventStore = mock(RunEventStore.class);
    private final CheckpointStore checkpointStore = mock(CheckpointStore.class);
    private final FakeAgentExecutor executor = new FakeAgentExecutor();
    private final List<String> cleanupOrder = new ArrayList<>();
    private HarnessOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        when(conversationStore.create(any(), any()))
                .thenReturn(new Conversation(
                        "conversation-1",
                        null,
                        Conversation.Status.REGULAR,
                        null,
                        Instant.EPOCH,
                        Instant.EPOCH
                ));
        when(conversationLock.tryAcquire(anyString(), anyString(), any()))
                .thenAnswer(invocation -> Optional.of(
                        new ConversationLock.Lease(
                                invocation.getArgument(0),
                                invocation.getArgument(1),
                                1
                        )
                ));
        when(conversationLock.renew(any())).thenReturn(true);
        when(conversationLock.release(any())).thenReturn(true);
        when(runStore.complete(anyString(), anyLong())).thenReturn(true);
        when(runStore.fail(
                anyString(),
                anyLong(),
                anyString(),
                anyString()
        )).thenReturn(true);
        when(runStore.cancel(anyString(), anyLong())).thenReturn(true);
        when(eventStore.append(any())).thenAnswer(invocation ->
                invocation.getArgument(0));
        when(checkpointStore.save(anyString(), anyString()))
                .thenAnswer(invocation -> new RunCheckpoint(
                        invocation.getArgument(0),
                        1,
                        invocation.getArgument(1),
                        Instant.EPOCH
                ));
        doAnswer(invocation -> {
            cleanupOrder.add("lock");
            return true;
        }).when(conversationLock).release(any());
        executor.onRelease = () -> cleanupOrder.add("executor");
        RunPersistenceService persistence = new RunPersistenceService(
                runStore,
                eventStore,
                checkpointStore,
                messageStore
        );
        ConversationContextLoader contextLoader =
                new ConversationContextLoader(
                        conversationStore,
                        messageStore,
                        20
                );
        RunFactory runFactory = new RunFactory(
                conversationLock,
                persistence,
                new AgentExecutionEventMapper()
        );
        orchestrator = new HarnessOrchestrator(
                new AgentExecutorRegistry(List.of(executor)),
                contextLoader,
                runFactory,
                conversationLock,
                persistence,
                Duration.ofMinutes(5)
        );
    }

    @Test
    void persistsAndEmitsCompletedRunLifecycle() {
        executor.events = Flux.just(
                new AgentExecutionEvent.TextDelta("hel"),
                new AgentExecutionEvent.TextDelta("lo"),
                new AgentExecutionEvent.Completed("hello", "model-a", 12)
        );

        StepVerifier.create(orchestrator.start(command()))
                .assertNext(event -> assertThat(event.event())
                        .isInstanceOf(RunEvent.RunStarted.class))
                .assertNext(event -> assertThat(event.event())
                        .isEqualTo(new RunEvent.TextDelta("hel")))
                .assertNext(event -> assertThat(event.event())
                        .isEqualTo(new RunEvent.TextDelta("lo")))
                .assertNext(event -> assertThat(event.event())
                        .isInstanceOf(RunEvent.RunCompleted.class))
                .verifyComplete();

        verify(runStore).complete(anyString(), eq(0L));
        ArgumentCaptor<com.alchemist.deepexplore.harness.domain.AgentRun> run =
                ArgumentCaptor.forClass(
                        com.alchemist.deepexplore.harness.domain.AgentRun.class
                );
        verify(runStore).create(run.capture());
        assertThat(run.getValue().workspaceId()).isEqualTo("workspace-1");
        assertThat(executor.lastRequest.workspaceId())
                .isEqualTo("workspace-1");
        verify(eventStore, atLeastOnce()).append(
                org.mockito.ArgumentMatchers.argThat(event ->
                        event.event() instanceof RunEvent.TextDelta)
        );
        verify(conversationLock).release(any(ConversationLock.Lease.class));
        assertThat(executor.released).isTrue();
        assertThat(cleanupOrder).containsExactly("executor", "lock");
    }

    @Test
    void invalidatesMemoryWhenExecutorFails() {
        executor.events = Flux.just(new AgentExecutionEvent.Failed(
                "MODEL_FAILED",
                "model failed"
        ));

        StepVerifier.create(orchestrator.start(command()))
                .assertNext(event -> assertThat(event.event())
                        .isInstanceOf(RunEvent.RunStarted.class))
                .assertNext(event -> assertThat(event.event())
                        .isEqualTo(new RunEvent.RunFailed(
                                "MODEL_FAILED",
                                "model failed"
                        )))
                .verifyComplete();

        verify(runStore).fail(
                anyString(),
                eq(0L),
                eq("MODEL_FAILED"),
                eq("model failed")
        );
        assertThat(executor.invalidated).isTrue();
        verify(conversationLock).release(any(ConversationLock.Lease.class));
    }

    @Test
    void rejectsRunWhenConversationIsBusy() {
        when(conversationLock.tryAcquire(anyString(), anyString(), any()))
                .thenReturn(Optional.empty());

        StepVerifier.create(orchestrator.start(command()))
                .assertNext(event -> assertThat(event.event())
                        .isInstanceOf(RunEvent.RunStarted.class))
                .assertNext(event -> assertThat(event.event())
                        .isEqualTo(new RunEvent.RunFailed(
                                "CONVERSATION_BUSY",
                                "该会话正在生成回复，请等待当前请求完成"
                        )))
                .verifyComplete();

        assertThat(executor.prepared).isFalse();
        assertThat(executor.released).isFalse();
        verify(conversationLock, never()).release(any());
    }

    @Test
    void rejectsCompletionAfterConversationLeaseIsLost() {
        when(conversationLock.renew(any())).thenReturn(false);
        executor.events = Flux.just(new AgentExecutionEvent.Completed(
                "stale answer",
                "model-a",
                10
        ));

        StepVerifier.create(orchestrator.start(command()))
                .assertNext(event -> assertThat(event.event())
                        .isInstanceOf(RunEvent.RunStarted.class))
                .assertNext(event -> assertThat(event.event())
                        .isEqualTo(new RunEvent.RunFailed(
                                "CONVERSATION_LEASE_LOST",
                                "会话执行租约已失效，本轮生成已停止"
                        )))
                .verifyComplete();

        verify(runStore, never()).complete(anyString(), anyLong());
        verify(runStore).fail(
                anyString(),
                eq(0L),
                eq("CONVERSATION_LEASE_LOST"),
                anyString()
        );
    }

    @Test
    void rewindsAgentStateForRegeneration() {
        when(messageStore.exists("conversation-1", "user-1")).thenReturn(true);
        executor.events = Flux.just(new AgentExecutionEvent.Completed(
                "regenerated",
                "model-a",
                10
        ));

        StepVerifier.create(orchestrator.start(command()))
                .expectNextCount(2)
                .verifyComplete();

        assertThat(executor.replay).isTrue();
        verify(messageStore).branch("conversation-1", "parent-1", 20);
    }

    @Test
    void passesTaskSearchProviderToExecutor() {
        executor.events = Flux.just(new AgentExecutionEvent.Completed(
                "searched",
                "model-a",
                10
        ));
        StartRunCommand command = new StartRunCommand(
                "conversation-1",
                "hello",
                "assistant",
                "fast",
                "user-1",
                "parent-1",
                "assistant-1",
                WebSearchProvider.TAVILY
        );

        StepVerifier.create(orchestrator.start(command))
                .expectNextCount(2)
                .verifyComplete();

        assertThat(executor.lastRequest.searchProvider())
                .isEqualTo(WebSearchProvider.TAVILY);
    }

    @Test
    void cancelsAndInvalidatesRunningExecution() {
        executor.events = Flux.never();

        StepVerifier.create(orchestrator.start(command()))
                .expectNextCount(1)
                .thenCancel()
                .verify();

        verify(runStore, timeout(1_000)).cancel(anyString(), eq(0L));
        assertThat(executor.invalidated).isTrue();
        verify(conversationLock, timeout(1_000))
                .release(any(ConversationLock.Lease.class));
    }

    private StartRunCommand command() {
        return new StartRunCommand(
                "conversation-1",
                "hello",
                "assistant",
                "fast",
                "user-1",
                "parent-1",
                "assistant-1",
                null,
                "workspace-1"
        );
    }

    private static final class FakeAgentExecutor implements AgentExecutor {

        private Flux<AgentExecutionEvent> events = Flux.empty();
        private boolean prepared;
        private boolean replay;
        private List<AgentMessage> history = List.of();
        private volatile boolean invalidated;
        private boolean released;
        private Runnable onRelease = () -> {
        };
        private AgentExecutionRequest lastRequest;

        @Override
        public String agentId() {
            return "assistant";
        }

        @Override
        public boolean isConfigured() {
            return true;
        }

        @Override
        public AgentStateSnapshot prepare(AgentPreparationRequest request) {
            this.prepared = true;
            this.replay = request.rebuildMemory();
            this.history = request.history();
            return new AgentStateSnapshot("[{\"stable\":true}]");
        }

        @Override
        public Flux<AgentExecutionEvent> execute(AgentExecutionRequest request) {
            lastRequest = request;
            return events;
        }

        @Override
        public void restore(String conversationId, AgentStateSnapshot snapshot) {
        }

        @Override
        public void invalidate(String conversationId) {
            invalidated = true;
        }

        @Override
        public void markMemorySynchronized(
                String conversationId,
                String sourceHeadMessageId
        ) {
        }

        @Override
        public void release(String conversationId, String runId) {
            released = true;
            onRelease.run();
        }
    }
}
