package com.alchemist.deepexplore.harness.application.execution;

import com.alchemist.deepexplore.agent.domain.AgentExecutionEvent;
import com.alchemist.deepexplore.agent.domain.AgentStateSnapshot;
import com.alchemist.deepexplore.agent.spi.AgentExecutor;
import com.alchemist.deepexplore.conversation.port.ConversationLock;
import com.alchemist.deepexplore.harness.domain.AgentRun;
import com.alchemist.deepexplore.harness.domain.RunEvent;
import com.alchemist.deepexplore.harness.domain.RunEventEnvelope;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;
import reactor.core.publisher.FluxSink;
import reactor.core.scheduler.Schedulers;

final class RunSession {

    private static final Logger log = LoggerFactory.getLogger(RunSession.class);

    private final AgentRun run;
    private final AgentExecutor executor;
    private final FluxSink<RunEventEnvelope> sink;
    private final ConversationLock conversationLock;
    private final RunPersistenceService persistence;
    private final AgentExecutionEventMapper eventMapper;
    private final AtomicLong sequence = new AtomicLong();
    private final AtomicBoolean terminal = new AtomicBoolean();
    private final AtomicBoolean lockAcquired = new AtomicBoolean();
    private final AtomicBoolean snapshotPrepared = new AtomicBoolean();
    private final AtomicReference<Disposable> subscription = new AtomicReference<>();
    private AgentStateSnapshot snapshot = AgentStateSnapshot.empty();

    RunSession(
            AgentRun run,
            AgentExecutor executor,
            FluxSink<RunEventEnvelope> sink,
            ConversationLock conversationLock,
            RunPersistenceService persistence,
            AgentExecutionEventMapper eventMapper
    ) {
        this.run = run;
        this.executor = executor;
        this.sink = sink;
        this.conversationLock = conversationLock;
        this.persistence = persistence;
        this.eventMapper = eventMapper;
    }

    AgentRun run() {
        return run;
    }

    void markLockAcquired() {
        lockAcquired.set(true);
    }

    void prepared(AgentStateSnapshot preparedSnapshot) {
        snapshot = preparedSnapshot;
        snapshotPrepared.set(true);
    }

    void attach(Disposable executionSubscription) {
        if (!subscription.compareAndSet(null, executionSubscription)
                || terminal.get()) {
            executionSubscription.dispose();
        }
    }

    void onAgentEvent(AgentExecutionEvent event) {
        switch (event) {
            case AgentExecutionEvent.Completed completed -> complete(completed);
            case AgentExecutionEvent.Failed failed ->
                    fail(failed.code(), failed.message());
            default -> emit(eventMapper.map(event));
        }
    }

    void onAgentStreamCompleted() {
        if (!terminal.get()) {
            fail("EXECUTOR_COMPLETED_WITHOUT_RESULT", "Agent 未返回最终结果");
        }
    }

    void fail(String code, String message) {
        if (!terminal.compareAndSet(false, true)) {
            return;
        }
        try {
            restoreSnapshot();
            RunEventEnvelope failedEvent = envelope(new RunEvent.RunFailed(
                    code,
                    message
            ));
            persistence.fail(run, code, message, failedEvent);
            sink.next(failedEvent);
        } finally {
            cleanup();
            sink.complete();
        }
    }

    void cancel() {
        Disposable current = subscription.get();
        if (current != null) {
            current.dispose();
        }
        Schedulers.boundedElastic().schedule(() -> {
            if (!terminal.compareAndSet(false, true)) {
                return;
            }
            try {
                restoreSnapshot();
                persistence.cancel(run, envelope(new RunEvent.RunCancelled()));
            } catch (RuntimeException error) {
                log.error("Unable to cancel run {}", run.id(), error);
            } finally {
                cleanup();
            }
        });
    }

    RunEventEnvelope envelope(RunEvent event) {
        return new RunEventEnvelope(
                UUID.randomUUID().toString(),
                run.id(),
                run.conversationId(),
                run.agentId(),
                sequence.incrementAndGet(),
                Instant.now(),
                event
        );
    }

    private void complete(AgentExecutionEvent.Completed completed) {
        if (!terminal.compareAndSet(false, true)) {
            return;
        }
        try {
            RunEventEnvelope completedEvent = envelope(new RunEvent.RunCompleted(
                    run.assistantMessageId(),
                    completed.model(),
                    completed.tokenUsage()
            ));
            persistence.complete(run, completed, completedEvent);
            sink.next(completedEvent);
            cleanup();
            sink.complete();
        } catch (RuntimeException error) {
            terminal.set(false);
            fail("RUN_COMMIT_FAILED", "Agent Run 结果提交失败");
        }
    }

    private void emit(RunEvent event) {
        RunEventEnvelope envelope = envelope(event);
        if (!(event instanceof RunEvent.TextDelta)) {
            persistence.appendEvent(envelope);
        }
        sink.next(envelope);
    }

    private void restoreSnapshot() {
        if (!snapshotPrepared.get()) {
            return;
        }
        try {
            executor.restore(run.conversationId(), snapshot);
        } catch (RuntimeException error) {
            log.error("Unable to restore run {}", run.id(), error);
        }
    }

    private void cleanup() {
        if (!lockAcquired.compareAndSet(true, false)) {
            return;
        }
        try {
            executor.release(run.conversationId());
        } catch (RuntimeException error) {
            log.error("Unable to release executor state for run {}", run.id(), error);
        } finally {
            try {
                conversationLock.release(run.conversationId());
            } catch (RuntimeException error) {
                log.error("Unable to release conversation lock for run {}", run.id(), error);
            }
        }
    }
}
