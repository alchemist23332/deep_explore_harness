package com.alchemist.deepexplore.harness.application.execution;

import com.alchemist.deepexplore.agent.spi.AgentExecutor;
import com.alchemist.deepexplore.conversation.domain.Conversation;
import com.alchemist.deepexplore.conversation.port.ConversationLock;
import com.alchemist.deepexplore.harness.application.command.StartRunCommand;
import com.alchemist.deepexplore.harness.domain.AgentRun;
import com.alchemist.deepexplore.harness.domain.RunEventEnvelope;
import com.alchemist.deepexplore.harness.domain.RunStatus;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;
import reactor.core.publisher.FluxSink;

@Component
final class RunFactory {

    private final ConversationLock conversationLock;
    private final RunPersistenceService persistence;
    private final AgentExecutionEventMapper eventMapper;

    RunFactory(
            ConversationLock conversationLock,
            RunPersistenceService persistence,
            AgentExecutionEventMapper eventMapper
    ) {
        this.conversationLock = conversationLock;
        this.persistence = persistence;
        this.eventMapper = eventMapper;
    }

    RunSession create(
            StartRunCommand command,
            Conversation conversation,
            AgentExecutor executor,
            FluxSink<RunEventEnvelope> sink
    ) {
        Instant now = Instant.now();
        AgentRun run = new AgentRun(
                UUID.randomUUID().toString(),
                conversation.id(),
                command.workspaceId(),
                valueOrRandom(command.userMessageId()),
                valueOrRandom(command.assistantMessageId()),
                command.agentId(),
                command.profileId(),
                RunStatus.RUNNING,
                null,
                null,
                now,
                now,
                null
        );
        return new RunSession(
                run,
                executor,
                sink,
                conversationLock,
                persistence,
                eventMapper
        );
    }

    private static String valueOrRandom(String value) {
        return value == null || value.isBlank()
                ? UUID.randomUUID().toString()
                : value;
    }
}
