package com.alchemist.deepexplore.harness.application.execution;

import com.alchemist.deepexplore.agent.application.AgentExecutorRegistry;
import com.alchemist.deepexplore.agent.spi.AgentExecutor;
import com.alchemist.deepexplore.conversation.port.ConversationLock;
import com.alchemist.deepexplore.harness.domain.AgentRun;
import com.alchemist.deepexplore.harness.domain.RunEvent;
import com.alchemist.deepexplore.harness.domain.RunEventEnvelope;
import com.alchemist.deepexplore.harness.port.RunStore;
import com.alchemist.deepexplore.support.BlockingExecution;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@Service
public class RunRecoveryService {

    private static final Logger log =
            LoggerFactory.getLogger(RunRecoveryService.class);

    private final RunStore runs;
    private final RunPersistenceService persistence;
    private final AgentExecutorRegistry executors;
    private final ConversationLock conversationLock;
    private final BlockingExecution blocking;

    public RunRecoveryService(
            RunStore runs,
            RunPersistenceService persistence,
            AgentExecutorRegistry executors,
            ConversationLock conversationLock,
            BlockingExecution blocking
    ) {
        this.runs = runs;
        this.persistence = persistence;
        this.executors = executors;
        this.conversationLock = conversationLock;
        this.blocking = blocking;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverInterruptedRuns() {
        blocking.mono(BlockingExecution.Kind.JDBC, () -> {
            runs.listRunning().forEach(this::recover);
            return true;
        }).subscribe(
                ignored -> {
                },
                error -> log.error("Unable to recover interrupted Runs", error)
        );
    }

    private void recover(AgentRun run) {
        String message = "服务重启导致本轮执行中断";
        try {
            persistence.fail(
                    run,
                    "PROCESS_RESTARTED",
                    message,
                    new RunEventEnvelope(
                            UUID.randomUUID().toString(),
                            run.id(),
                            run.conversationId(),
                            run.agentId(),
                            0,
                            Instant.now(),
                            new RunEvent.RunFailed(
                                    "PROCESS_RESTARTED",
                                    message
                            )
                    )
            );
            AgentExecutor executor = executors.require(run.agentId());
            executor.invalidate(run.conversationId());
            executor.release(run.conversationId(), run.id());
        } catch (RuntimeException error) {
            log.warn("Unable to recover interrupted run {}", run.id(), error);
        } finally {
            conversationLock.releaseOwnedBy(
                    run.conversationId(),
                    run.id()
            );
        }
    }
}
