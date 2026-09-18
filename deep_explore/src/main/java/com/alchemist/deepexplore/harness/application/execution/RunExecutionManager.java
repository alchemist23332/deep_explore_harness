package com.alchemist.deepexplore.harness.application.execution;

import com.alchemist.deepexplore.harness.application.command.StartRunCommand;
import com.alchemist.deepexplore.harness.domain.AgentRun;
import com.alchemist.deepexplore.harness.domain.RunEvent;
import com.alchemist.deepexplore.harness.domain.RunEventEnvelope;
import com.alchemist.deepexplore.harness.port.RunEventStore;
import com.alchemist.deepexplore.harness.port.RunStore;
import com.alchemist.deepexplore.support.BlockingExecution;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Service;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

@Service
public class RunExecutionManager {

    private static final int LIVE_EVENT_REPLAY_LIMIT = 8_192;

    private final HarnessService harness;
    private final RunStore runStore;
    private final RunEventStore eventStore;
    private final RunPersistenceService persistence;
    private final BlockingExecution blocking;
    private final ConcurrentMap<String, LiveRun> liveRuns =
            new ConcurrentHashMap<>();

    public RunExecutionManager(
            HarnessService harness,
            RunStore runStore,
            RunEventStore eventStore,
            RunPersistenceService persistence,
            BlockingExecution blocking
    ) {
        this.harness = harness;
        this.runStore = runStore;
        this.eventStore = eventStore;
        this.persistence = persistence;
        this.blocking = blocking;
    }

    public Mono<StartedRun> start(StartRunCommand command) {
        return Mono.create(result -> {
            LiveRun liveRun = new LiveRun();
            Disposable execution = harness.start(command).subscribe(
                    event -> {
                        if (event.event() instanceof RunEvent.RunStarted started
                                && liveRun.register(event.runId())) {
                            liveRuns.put(event.runId(), liveRun);
                            result.success(new StartedRun(
                                    event.runId(),
                                    event.conversationId(),
                                    started.assistantMessageId()
                            ));
                        }
                        liveRun.emit(event);
                    },
                    error -> {
                        liveRun.fail(error);
                        if (!liveRun.registered()) {
                            result.error(error);
                        }
                        remove(liveRun);
                    },
                    () -> {
                        liveRun.complete();
                        if (!liveRun.registered()) {
                            result.error(new IllegalStateException(
                                    "Run completed before RunStarted"
                            ));
                        }
                        remove(liveRun);
                    }
            );
            liveRun.attach(execution);
        });
    }

    public Mono<AgentRun> get(String runId) {
        return blocking.mono(
                BlockingExecution.Kind.JDBC,
                () -> requireRun(runId)
        );
    }

    public Flux<RunEventEnvelope> events(String runId, long afterSequence) {
        long safeSequence = Math.max(0, afterSequence);
        return blocking.mono(BlockingExecution.Kind.JDBC, () -> {
            requireRun(runId);
            return eventStore.list(runId, safeSequence);
        }).flatMapMany(history -> {
            long watermark = history.stream()
                    .mapToLong(RunEventEnvelope::sequence)
                    .max()
                    .orElse(safeSequence);
            Flux<RunEventEnvelope> persisted = Flux.fromIterable(history);
            LiveRun liveRun = liveRuns.get(runId);
            if (liveRun == null) {
                return persisted;
            }
            Flux<RunEventEnvelope> live = liveRun.events()
                    .filter(event -> event.sequence() > watermark);
            return Flux.concat(persisted, live)
                    .distinct(RunEventEnvelope::eventId);
        });
    }

    public Mono<String> searchProvider(String runId) {
        return blocking.mono(
                BlockingExecution.Kind.JDBC,
                () -> eventStore.list(runId, 0).stream()
                .map(RunEventEnvelope::event)
                .filter(RunEvent.RunStarted.class::isInstance)
                .map(RunEvent.RunStarted.class::cast)
                .map(RunEvent.RunStarted::searchProvider)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse("")
        );
    }

    public Mono<Boolean> cancel(String runId) {
        LiveRun liveRun = liveRuns.get(runId);
        if (liveRun != null) {
            liveRun.cancel();
            return Mono.just(true);
        }
        return blocking.mono(BlockingExecution.Kind.JDBC, () -> {
            AgentRun run = requireRun(runId);
            if (run.status().isTerminal()) {
                return false;
            }
            RunEventEnvelope cancelled = new RunEventEnvelope(
                    UUID.randomUUID().toString(),
                    run.id(),
                    run.conversationId(),
                    run.agentId(),
                    0,
                    Instant.now(),
                    new RunEvent.RunCancelled()
            );
            persistence.cancel(run, cancelled);
            return true;
        });
    }

    private AgentRun requireRun(String runId) {
        return runStore.find(runId)
                .orElseThrow(() -> new RunNotFoundException(runId));
    }

    private void remove(LiveRun liveRun) {
        String runId = liveRun.runId();
        if (runId != null) {
            liveRuns.remove(runId, liveRun);
        }
    }

    public record StartedRun(
            String runId,
            String conversationId,
            String assistantMessageId
    ) {
    }

    private static final class LiveRun {

        private final Sinks.Many<RunEventEnvelope> sink =
                Sinks.many().replay().limit(LIVE_EVENT_REPLAY_LIMIT);
        private final AtomicReference<Disposable> execution =
                new AtomicReference<>();
        private final AtomicBoolean finished = new AtomicBoolean();
        private final AtomicReference<String> runId = new AtomicReference<>();

        private boolean register(String value) {
            return runId.compareAndSet(null, value);
        }

        private boolean registered() {
            return runId.get() != null;
        }

        private String runId() {
            return runId.get();
        }

        private void attach(Disposable value) {
            if (!execution.compareAndSet(null, value) || finished.get()) {
                value.dispose();
            }
        }

        private void emit(RunEventEnvelope event) {
            sink.tryEmitNext(event);
        }

        private Flux<RunEventEnvelope> events() {
            return sink.asFlux();
        }

        private void cancel() {
            Disposable current = execution.get();
            if (current != null) {
                current.dispose();
            }
        }

        private void complete() {
            finished.set(true);
            sink.tryEmitComplete();
        }

        private void fail(Throwable error) {
            finished.set(true);
            sink.tryEmitError(error);
        }
    }
}
