package com.alchemist.deepexplore.harness.application;

import com.alchemist.deepexplore.agent.application.AgentExecutorRegistry;
import com.alchemist.deepexplore.agent.domain.AgentMessage;
import com.alchemist.deepexplore.agent.domain.AgentPreparationRequest;
import com.alchemist.deepexplore.agent.domain.AgentExecutionRequest;
import com.alchemist.deepexplore.agent.domain.AgentStateSnapshot;
import com.alchemist.deepexplore.agent.spi.AgentExecutor;
import com.alchemist.deepexplore.conversation.domain.Conversation;
import com.alchemist.deepexplore.conversation.domain.ConversationMessage;
import com.alchemist.deepexplore.conversation.port.ConversationLock;
import com.alchemist.deepexplore.conversation.port.ConversationStore;
import com.alchemist.deepexplore.conversation.port.MessageStore;
import com.alchemist.deepexplore.harness.domain.AgentRun;
import com.alchemist.deepexplore.harness.domain.RunEvent;
import com.alchemist.deepexplore.harness.domain.RunEventEnvelope;
import com.alchemist.deepexplore.harness.domain.RunStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.scheduler.Schedulers;

@Service
public class HarnessOrchestrator implements HarnessService {

    private static final Logger log = LoggerFactory.getLogger(HarnessOrchestrator.class);

    private final AgentExecutorRegistry agentRegistry;
    private final ConversationStore conversationStore;
    private final MessageStore messageStore;
    private final ConversationLock conversationLock;
    private final RunPersistenceService persistence;
    private final AgentExecutionEventMapper eventMapper;
    private final Duration generationLockTimeout;
    private final int maxMessages;

    public HarnessOrchestrator(
            AgentExecutorRegistry agentRegistry,
            ConversationStore conversationStore,
            MessageStore messageStore,
            ConversationLock conversationLock,
            RunPersistenceService persistence,
            AgentExecutionEventMapper eventMapper,
            @Value("${app.conversation.generation-lock-timeout:5m}")
            Duration generationLockTimeout,
            @Value("${app.conversation.max-messages:20}") int maxMessages
    ) {
        this.agentRegistry = agentRegistry;
        this.conversationStore = conversationStore;
        this.messageStore = messageStore;
        this.conversationLock = conversationLock;
        this.persistence = persistence;
        this.eventMapper = eventMapper;
        this.generationLockTimeout = generationLockTimeout;
        this.maxMessages = maxMessages;
    }

    @Override
    public Flux<RunEventEnvelope> start(StartRunCommand command) {
        return Flux.<RunEventEnvelope>create(sink -> start(command, sink))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private void start(
            StartRunCommand command,
            FluxSink<RunEventEnvelope> sink
    ) {
        AgentExecutor executor = agentRegistry.require(command.agentId());
        Conversation conversation = conversationStore.create(
                command.conversationId(),
                null
        );
        RunSession session = createRunSession(command, conversation, executor, sink);
        RunEventEnvelope startedEvent = session.envelope(new RunEvent.RunStarted(
                session.run().profileId(),
                session.run().userMessageId(),
                session.run().assistantMessageId(),
                command.searchProvider() == null
                        ? null
                        : command.searchProvider().name()
        ));
        persistence.start(session.run(), startedEvent);
        sink.next(startedEvent);

        if (!executor.isConfigured()) {
            session.fail(
                    "MODEL_NOT_CONFIGURED",
                    "服务端尚未配置 AI_API_KEY"
            );
            return;
        }

        if (!conversationLock.tryAcquire(conversation.id(), generationLockTimeout)) {
            session.fail(
                    "CONVERSATION_BUSY",
                    "该会话正在生成回复，请等待当前请求完成"
            );
            return;
        }
        session.markLockAcquired();

        try {
            boolean replay = messageStore.exists(
                    conversation.id(),
                    session.run().userMessageId()
            );
            AgentStateSnapshot snapshot = executor.prepare(
                    new AgentPreparationRequest(
                            conversation.id(),
                            replay,
                            history(conversation, command, replay)
                    )
            );
            session.prepared(snapshot);
            persistence.checkpoint(
                    session.run(),
                    snapshot.payload(),
                    version -> session.envelope(
                            new RunEvent.CheckpointSaved(version)
                    )
            );

            messageStore.append(
                    conversation.id(),
                    session.run().userMessageId(),
                    command.userParentMessageId(),
                    ConversationMessage.Role.USER,
                    command.message(),
                    ConversationMessage.Status.COMPLETE,
                    null,
                    null
            );

            AgentExecutionRequest executionRequest = new AgentExecutionRequest(
                    session.run().id(),
                    conversation.id(),
                    session.run().agentId(),
                    session.run().profileId(),
                    command.message(),
                    command.searchProvider()
            );
            sink.onCancel(session::cancel);
            Disposable subscription = executor.execute(executionRequest)
                    .publishOn(Schedulers.boundedElastic())
                    .subscribe(
                            session::onAgentEvent,
                            error -> session.fail(
                                    "EXECUTOR_STREAM_FAILED",
                                    "Agent 执行流异常"
                            ),
                            session::onAgentStreamCompleted
                    );
            session.attach(subscription);
        } catch (RuntimeException error) {
            log.error("Unable to start run {}", session.run().id(), error);
            session.fail("RUN_START_FAILED", "Agent Run 启动失败");
        }
    }

    private List<AgentMessage> history(
            Conversation conversation,
            StartRunCommand command,
            boolean replay
    ) {
        String headMessageId = replay
                ? command.userParentMessageId()
                : conversation.headMessageId();
        return messageStore.branch(
                        conversation.id(),
                        headMessageId,
                        maxMessages
                ).stream()
                .map(message -> new AgentMessage(
                        message.role() == ConversationMessage.Role.USER
                                ? AgentMessage.Role.USER
                                : AgentMessage.Role.ASSISTANT,
                        message.content()
                ))
                .toList();
    }

    private RunSession createRunSession(
            StartRunCommand command,
            Conversation conversation,
            AgentExecutor executor,
            FluxSink<RunEventEnvelope> sink
    ) {
        Instant now = Instant.now();
        AgentRun run = new AgentRun(
                UUID.randomUUID().toString(),
                conversation.id(),
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
