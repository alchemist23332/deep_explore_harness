package com.alchemist.deepexplore.harness.application.execution;

import com.alchemist.deepexplore.agent.application.AgentExecutorRegistry;
import com.alchemist.deepexplore.agent.domain.AgentPreparationRequest;
import com.alchemist.deepexplore.agent.domain.AgentExecutionRequest;
import com.alchemist.deepexplore.agent.domain.AgentStateSnapshot;
import com.alchemist.deepexplore.agent.spi.AgentExecutor;
import com.alchemist.deepexplore.conversation.domain.Conversation;
import com.alchemist.deepexplore.conversation.port.ConversationLock;
import com.alchemist.deepexplore.harness.application.command.StartRunCommand;
import com.alchemist.deepexplore.harness.domain.RunEvent;
import com.alchemist.deepexplore.harness.domain.RunEventEnvelope;
import java.time.Duration;
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
    private final ConversationContextLoader contextLoader;
    private final RunFactory runFactory;
    private final ConversationLock conversationLock;
    private final RunPersistenceService persistence;
    private final Duration generationLockTimeout;

    public HarnessOrchestrator(
            AgentExecutorRegistry agentRegistry,
            ConversationContextLoader contextLoader,
            RunFactory runFactory,
            ConversationLock conversationLock,
            RunPersistenceService persistence,
            @Value("${app.conversation.generation-lock-timeout:5m}")
            Duration generationLockTimeout
    ) {
        this.agentRegistry = agentRegistry;
        this.contextLoader = contextLoader;
        this.runFactory = runFactory;
        this.conversationLock = conversationLock;
        this.persistence = persistence;
        this.generationLockTimeout = generationLockTimeout;
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
        Conversation conversation = contextLoader.open(command);
        RunSession session = runFactory.create(
                command,
                conversation,
                executor,
                sink
        );
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
            ConversationContextLoader.Context context = contextLoader.load(
                    conversation,
                    command,
                    session.run().userMessageId()
            );
            AgentStateSnapshot snapshot = executor.prepare(
                    new AgentPreparationRequest(
                            conversation.id(),
                            context.replay(),
                            context.history()
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

            contextLoader.appendUserMessage(session.run(), command);

            AgentExecutionRequest executionRequest = new AgentExecutionRequest(
                    session.run().id(),
                    conversation.id(),
                    session.run().agentId(),
                    session.run().profileId(),
                    command.message(),
                    command.searchProvider(),
                    command.workspaceId()
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
}
