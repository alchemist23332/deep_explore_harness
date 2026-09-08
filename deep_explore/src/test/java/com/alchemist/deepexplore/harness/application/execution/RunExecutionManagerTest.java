package com.alchemist.deepexplore.harness.application.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.alchemist.deepexplore.harness.application.command.StartRunCommand;
import com.alchemist.deepexplore.harness.domain.AgentRun;
import com.alchemist.deepexplore.harness.domain.RunEvent;
import com.alchemist.deepexplore.harness.domain.RunEventEnvelope;
import com.alchemist.deepexplore.harness.domain.RunStatus;
import com.alchemist.deepexplore.harness.port.RunEventStore;
import com.alchemist.deepexplore.harness.port.RunStore;
import com.alchemist.deepexplore.support.BlockingExecution;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

class RunExecutionManagerTest {

    @Test
    void eventSubscriberCancellationDoesNotCancelRun() {
        HarnessService harness = mock(HarnessService.class);
        RunStore runStore = mock(RunStore.class);
        RunEventStore eventStore = mock(RunEventStore.class);
        RunEventEnvelope started = started();
        AtomicBoolean executionCancelled = new AtomicBoolean();
        when(harness.start(command())).thenReturn(
                Flux.concat(Flux.just(started), Flux.never())
                        .doOnCancel(() -> executionCancelled.set(true))
        );
        when(runStore.find("run-1")).thenReturn(Optional.of(run()));
        when(eventStore.list("run-1", 0)).thenReturn(List.of(started));
        RunExecutionManager manager = new RunExecutionManager(
                harness,
                runStore,
                eventStore,
                mock(RunPersistenceService.class),
                new BlockingExecution()
        );

        assertThat(manager.start(command()).block().runId()).isEqualTo("run-1");

        StepVerifier.create(manager.events("run-1", 0))
                .assertNext(event -> assertThat(event.runId()).isEqualTo("run-1"))
                .thenCancel()
                .verify();

        assertThat(executionCancelled).isFalse();
        assertThat(manager.cancel("run-1").block()).isTrue();
        assertThat(executionCancelled).isTrue();
    }

    private static StartRunCommand command() {
        return new StartRunCommand(
                "conversation-1",
                "hello",
                "assistant",
                "fast",
                "user-1",
                null,
                "assistant-1"
        );
    }

    private static RunEventEnvelope started() {
        return new RunEventEnvelope(
                "event-1",
                "run-1",
                "conversation-1",
                "assistant",
                1,
                Instant.EPOCH,
                new RunEvent.RunStarted(
                        "fast",
                        "user-1",
                        "assistant-1",
                        "TAVILY"
                )
        );
    }

    private static AgentRun run() {
        return new AgentRun(
                "run-1",
                "conversation-1",
                null,
                "user-1",
                "assistant-1",
                "assistant",
                "fast",
                RunStatus.RUNNING,
                null,
                null,
                Instant.EPOCH,
                Instant.EPOCH,
                null
        );
    }
}
