package com.alchemist.deepexplore.harness.application.execution;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import com.alchemist.deepexplore.agent.spi.AgentExecutor;
import com.alchemist.deepexplore.conversation.port.ConversationLock;
import com.alchemist.deepexplore.harness.domain.AgentRun;
import com.alchemist.deepexplore.harness.domain.RunEventEnvelope;
import com.alchemist.deepexplore.harness.domain.RunStatus;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import reactor.core.Disposable;
import reactor.core.publisher.FluxSink;

class RunSessionTest {

    @Test
    @SuppressWarnings("unchecked")
    void disposesSubscriptionAttachedAfterCancellation() {
        AgentExecutor executor = mock(AgentExecutor.class);
        RunPersistenceService persistence = mock(RunPersistenceService.class);
        RunSession session = new RunSession(
                run(),
                executor,
                mock(FluxSink.class),
                mock(ConversationLock.class),
                persistence,
                mock(AgentExecutionEventMapper.class)
        );
        session.prepared();
        Disposable lateSubscription = mock(Disposable.class);

        session.cancel();
        session.attach(lateSubscription);

        verify(lateSubscription).dispose();
        verify(executor, timeout(1_000)).invalidate("conversation-1");
        verify(persistence, timeout(1_000)).cancel(
                any(AgentRun.class),
                any(RunEventEnvelope.class)
        );
    }

    private static AgentRun run() {
        return new AgentRun(
                "run-1",
                "conversation-1",
                "workspace-1",
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
