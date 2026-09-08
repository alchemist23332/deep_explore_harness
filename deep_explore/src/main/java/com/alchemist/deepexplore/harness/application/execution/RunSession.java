package com.alchemist.deepexplore.harness.application.execution;

import com.alchemist.deepexplore.agent.domain.AgentExecutionEvent;
import com.alchemist.deepexplore.agent.spi.AgentExecutor;
import com.alchemist.deepexplore.conversation.port.ConversationLock;
import com.alchemist.deepexplore.harness.domain.AgentRun;
import com.alchemist.deepexplore.harness.domain.RunEvent;
import com.alchemist.deepexplore.harness.domain.RunEventEnvelope;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;
import reactor.core.Disposables;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.scheduler.Schedulers;

final class RunSession {

    private static final Logger log = LoggerFactory.getLogger(RunSession.class);
    private static final Disposable TERMINATED_SUBSCRIPTION =
            Disposables.disposed();

    private final AgentRun run;
    private final AgentExecutor executor;
    private final FluxSink<RunEventEnvelope> sink;
    private final ConversationLock conversationLock;
    private final RunPersistenceService persistence;
    private final AgentExecutionEventMapper eventMapper;
    private final AtomicBoolean terminal = new AtomicBoolean();
    private final AtomicBoolean snapshotPrepared = new AtomicBoolean();
    private final AtomicReference<Disposable> subscription = new AtomicReference<>();
    private final AtomicReference<Disposable> leaseRenewal = new AtomicReference<>();
    private final AtomicReference<ConversationLock.Lease> lease =
            new AtomicReference<>();

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

    void markLockAcquired(
            ConversationLock.Lease acquiredLease,
            Duration timeout
    ) {
        if (!lease.compareAndSet(null, acquiredLease)) {
            throw new IllegalStateException("Conversation lease already attached");
        }
        Duration renewalInterval = Duration.ofMillis(Math.max(
                100,
                timeout.toMillis() / 3
        ));
        Disposable renewal = Flux.interval(
                        renewalInterval,
                        renewalInterval,
                        Schedulers.boundedElastic()
                )
                .subscribe(
                        ignored -> renewLease(),
                        error -> leaseLost(error)
                );
        if (!leaseRenewal.compareAndSet(null, renewal) || terminal.get()) {
            renewal.dispose();
        }
    }

    void prepared() {
        snapshotPrepared.set(true);
    }

    void attach(Disposable executionSubscription) {
        if (!subscription.compareAndSet(null, executionSubscription)) {
            executionSubscription.dispose();
            return;
        }
        if (terminal.get()
                && subscription.compareAndSet(
                        executionSubscription,
                        TERMINATED_SUBSCRIPTION
                )) {
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
        disposeExecution();
        try {
            invalidatePreparedState();
            RunEventEnvelope failedEvent = persistence.fail(
                    run,
                    code,
                    message,
                    envelope(new RunEvent.RunFailed(code, message))
            );
            sink.next(failedEvent);
        } finally {
            cleanup();
            sink.complete();
        }
    }

    void cancel() {
        if (!terminal.compareAndSet(false, true)) {
            return;
        }
        disposeExecution();
        Schedulers.boundedElastic().schedule(() -> {
            try {
                invalidatePreparedState();
                persistence.cancel(
                        run,
                        envelope(new RunEvent.RunCancelled())
                );
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
                0,
                Instant.now(),
                event
        );
    }

    private void complete(AgentExecutionEvent.Completed completed) {
        if (!terminal.compareAndSet(false, true)) {
            return;
        }
        stopLeaseRenewal();
        try {
            if (!renewLeaseOwnership()) {
                terminal.set(false);
                leaseLost(null);
                return;
            }
        } catch (RuntimeException error) {
            terminal.set(false);
            leaseLost(error);
            return;
        }
        try {
            RunEventEnvelope completedEvent = persistence.complete(
                    run,
                    completed,
                    envelope(new RunEvent.RunCompleted(
                            run.assistantMessageId(),
                            completed.model(),
                            completed.tokenUsage()
                    ))
            );
            synchronizeMemory();
            sink.next(completedEvent);
            cleanup();
            sink.complete();
        } catch (RuntimeException error) {
            terminal.set(false);
            fail("RUN_COMMIT_FAILED", "Agent Run 结果提交失败");
        }
    }

    private void emit(RunEvent event) {
        sink.next(persistence.appendEvent(envelope(event)));
    }

    private void invalidatePreparedState() {
        if (!snapshotPrepared.get()) {
            return;
        }
        try {
            executor.invalidate(run.conversationId());
        } catch (RuntimeException error) {
            log.error("Unable to invalidate memory for run {}", run.id(), error);
        }
    }

    private void synchronizeMemory() {
        try {
            executor.markMemorySynchronized(
                    run.conversationId(),
                    run.assistantMessageId()
            );
        } catch (RuntimeException error) {
            log.error("Unable to synchronize memory for run {}", run.id(), error);
            invalidatePreparedState();
        }
    }

    private void cleanup() {
        stopLeaseRenewal();
        ConversationLock.Lease acquiredLease = lease.getAndSet(null);
        if (acquiredLease == null) {
            return;
        }
        try {
            executor.release(run.conversationId(), run.id());
        } catch (RuntimeException error) {
            log.error("Unable to release executor state for run {}", run.id(), error);
        } finally {
            try {
                if (!conversationLock.release(acquiredLease)) {
                    log.warn(
                            "Conversation lease was no longer owned by run {}",
                            run.id()
                    );
                }
            } catch (RuntimeException error) {
                log.error("Unable to release conversation lock for run {}", run.id(), error);
            }
        }
    }

    private void stopLeaseRenewal() {
        Disposable renewal = leaseRenewal.getAndSet(TERMINATED_SUBSCRIPTION);
        if (renewal != null && renewal != TERMINATED_SUBSCRIPTION) {
            renewal.dispose();
        }
    }

    private void disposeExecution() {
        Disposable current = subscription.getAndSet(TERMINATED_SUBSCRIPTION);
        if (current != null && current != TERMINATED_SUBSCRIPTION) {
            current.dispose();
        }
    }

    private void renewLease() {
        if (terminal.get()) {
            return;
        }
        ConversationLock.Lease current = lease.get();
        if (current == null) {
            return;
        }
        try {
            if (!renewLeaseOwnership()) {
                leaseLost(null);
            }
        } catch (RuntimeException error) {
            leaseLost(error);
        }
    }

    private void leaseLost(Throwable error) {
        if (error != null) {
            log.error("Unable to renew conversation lease for run {}", run.id(), error);
        }
        fail(
                "CONVERSATION_LEASE_LOST",
                "会话执行租约已失效，本轮生成已停止"
        );
    }

    private boolean renewLeaseOwnership() {
        ConversationLock.Lease current = lease.get();
        return current != null && conversationLock.renew(current);
    }
}
