package com.alchemist.deepexplore.harness.application;

import com.alchemist.deepexplore.agent.domain.AgentExecutionEvent;
import com.alchemist.deepexplore.conversation.domain.ConversationMessage;
import com.alchemist.deepexplore.conversation.port.MessageStore;
import com.alchemist.deepexplore.harness.domain.AgentRun;
import com.alchemist.deepexplore.harness.domain.RunCheckpoint;
import com.alchemist.deepexplore.harness.domain.RunEventEnvelope;
import com.alchemist.deepexplore.harness.port.CheckpointStore;
import com.alchemist.deepexplore.harness.port.RunEventStore;
import com.alchemist.deepexplore.harness.port.RunStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RunPersistenceService {

    private final RunStore runStore;
    private final RunEventStore eventStore;
    private final CheckpointStore checkpointStore;
    private final MessageStore messageStore;

    public RunPersistenceService(
            RunStore runStore,
            RunEventStore eventStore,
            CheckpointStore checkpointStore,
            MessageStore messageStore
    ) {
        this.runStore = runStore;
        this.eventStore = eventStore;
        this.checkpointStore = checkpointStore;
        this.messageStore = messageStore;
    }

    @Transactional
    public void start(AgentRun run, RunEventEnvelope startedEvent) {
        runStore.create(run);
        eventStore.append(startedEvent);
    }

    @Transactional
    public RunCheckpoint checkpoint(
            AgentRun run,
            String stateJson,
            java.util.function.LongFunction<RunEventEnvelope> eventFactory
    ) {
        RunCheckpoint checkpoint = checkpointStore.save(run.id(), stateJson);
        eventStore.append(eventFactory.apply(checkpoint.version()));
        return checkpoint;
    }

    public void appendEvent(RunEventEnvelope event) {
        eventStore.append(event);
    }

    @Transactional
    public void complete(
            AgentRun run,
            AgentExecutionEvent.Completed completed,
            RunEventEnvelope completedEvent
    ) {
        messageStore.append(
                run.conversationId(),
                run.assistantMessageId(),
                run.userMessageId(),
                ConversationMessage.Role.ASSISTANT,
                completed.text(),
                ConversationMessage.Status.COMPLETE,
                completed.model(),
                completed.tokenUsage()
        );
        runStore.complete(run.id());
        eventStore.append(completedEvent);
    }

    @Transactional
    public void fail(
            AgentRun run,
            String code,
            String message,
            RunEventEnvelope failedEvent
    ) {
        runStore.fail(run.id(), code, message);
        eventStore.append(failedEvent);
    }

    @Transactional
    public void cancel(AgentRun run, RunEventEnvelope cancelledEvent) {
        runStore.cancel(run.id());
        eventStore.append(cancelledEvent);
    }
}
