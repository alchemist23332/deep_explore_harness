package com.alchemist.deepexplore.harness.application;

import com.alchemist.deepexplore.agent.application.AgentExecutorRegistry;
import com.alchemist.deepexplore.agent.domain.AgentExecutionEvent;
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
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
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
    private final Duration generationLockTimeout;

    public HarnessOrchestrator(
            AgentExecutorRegistry agentRegistry,
            ConversationStore conversationStore,
            MessageStore messageStore,
            ConversationLock conversationLock,
            RunPersistenceService persistence,
            @Value("${app.conversation.generation-lock-timeout:5m}")
            Duration generationLockTimeout
    ) {
        this.agentRegistry = agentRegistry;
        this.conversationStore = conversationStore;
        this.messageStore = messageStore;
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
        Conversation conversation = conversationStore.create(
                command.conversationId(),
                null
        );
        RunContext context = createRunContext(command, conversation, executor, sink);
        RunEventEnvelope startedEvent = context.envelope(new RunEvent.RunStarted(
                context.run.profileId(),
                context.run.userMessageId()
        ));
        persistence.start(context.run, startedEvent);
        sink.next(startedEvent);

        if (!executor.isConfigured()) {
            context.fail(
                    "MODEL_NOT_CONFIGURED",
                    "服务端尚未配置 AI_API_KEY"
            );
            return;
        }

        if (!conversationLock.tryAcquire(conversation.id(), generationLockTimeout)) {
            context.fail(
                    "CONVERSATION_BUSY",
                    "该会话正在生成回复，请等待当前请求完成"
            );
            return;
        }
        context.lockAcquired.set(true);

        try {
            boolean replay = messageStore.exists(
                    conversation.id(),
                    context.run.userMessageId()
            );
            context.snapshot = executor.prepare(
                    conversation.id(),
                    replay,
                    command.userParentMessageId()
            );
            context.snapshotPrepared.set(true);
            persistence.checkpoint(
                    context.run,
                    context.snapshot.payload(),
                    version -> context.envelope(new RunEvent.CheckpointSaved(version))
            );

            messageStore.append(
                    conversation.id(),
                    context.run.userMessageId(),
                    command.userParentMessageId(),
                    ConversationMessage.Role.USER,
                    command.message(),
                    ConversationMessage.Status.COMPLETE,
                    null,
                    null
            );

            AgentExecutionRequest executionRequest = new AgentExecutionRequest(
                    context.run.id(),
                    conversation.id(),
                    context.run.agentId(),
                    context.run.profileId(),
                    command.message()
            );
            Disposable subscription = executor.execute(executionRequest)
                    .publishOn(Schedulers.boundedElastic())
                    .subscribe(
                            context::onAgentEvent,
                            error -> context.fail(
                                    "EXECUTOR_STREAM_FAILED",
                                    "Agent 执行流异常"
                            ),
                            context::onAgentStreamCompleted
                    );
            context.subscription.set(subscription);
            sink.onCancel(context::cancel);
        } catch (RuntimeException error) {
            log.error("Unable to start run {}", context.run.id(), error);
            context.fail("RUN_START_FAILED", "Agent Run 启动失败");
        }
    }

    private RunContext createRunContext(
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
        return new RunContext(run, executor, sink);
    }

    private final class RunContext {

        private final AgentRun run;
        private final AgentExecutor executor;
        private final FluxSink<RunEventEnvelope> sink;
        private final AtomicLong sequence = new AtomicLong();
        private final AtomicBoolean terminal = new AtomicBoolean();
        private final AtomicBoolean lockAcquired = new AtomicBoolean();
        private final AtomicBoolean snapshotPrepared = new AtomicBoolean();
        private final AtomicReference<Disposable> subscription = new AtomicReference<>();
        private AgentStateSnapshot snapshot = AgentStateSnapshot.empty();

        private RunContext(
                AgentRun run,
                AgentExecutor executor,
                FluxSink<RunEventEnvelope> sink
        ) {
            this.run = run;
            this.executor = executor;
            this.sink = sink;
        }

        private void onAgentEvent(AgentExecutionEvent event) {
            switch (event) {
                case AgentExecutionEvent.TextDelta delta ->
                        emit(new RunEvent.TextDelta(delta.text()));
                case AgentExecutionEvent.ToolCallStarted tool ->
                        emit(new RunEvent.ToolCallStarted(
                                tool.toolCallId(),
                                tool.toolName(),
                                tool.argumentsJson()
                        ));
                case AgentExecutionEvent.ToolCallCompleted tool ->
                        emit(new RunEvent.ToolCallCompleted(
                                tool.toolCallId(),
                                tool.toolName(),
                                tool.resultJson(),
                                tool.success()
                        ));
                case AgentExecutionEvent.ApprovalRequired approval ->
                        emit(new RunEvent.ApprovalRequired(
                                approval.approvalId(),
                                approval.prompt()
                        ));
                case AgentExecutionEvent.ArtifactProduced artifact ->
                        emit(new RunEvent.ArtifactProduced(
                                artifact.artifactId(),
                                artifact.kind(),
                                artifact.uri(),
                                artifact.metadata()
                        ));
                case AgentExecutionEvent.Completed completed ->
                        complete(completed);
                case AgentExecutionEvent.Failed failed ->
                        fail(failed.code(), failed.message());
            }
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

        private void fail(String code, String message) {
            if (!terminal.compareAndSet(false, true)) {
                return;
            }
            try {
                if (snapshotPrepared.get()) {
                    try {
                        executor.restore(run.conversationId(), snapshot);
                    } catch (RuntimeException error) {
                        log.error("Unable to restore run {}", run.id(), error);
                    }
                }
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

        private void cancel() {
            Disposable current = subscription.get();
            if (current != null) {
                current.dispose();
            }
            Schedulers.boundedElastic().schedule(() -> {
                if (!terminal.compareAndSet(false, true)) {
                    return;
                }
                try {
                    if (snapshotPrepared.get()) {
                        try {
                            executor.restore(run.conversationId(), snapshot);
                        } catch (RuntimeException error) {
                            log.error("Unable to restore cancelled run {}", run.id(), error);
                        }
                    }
                    persistence.cancel(run, envelope(new RunEvent.RunCancelled()));
                } catch (RuntimeException error) {
                    log.error("Unable to cancel run {}", run.id(), error);
                } finally {
                    cleanup();
                }
            });
        }

        private void onAgentStreamCompleted() {
            if (!terminal.get()) {
                fail("EXECUTOR_COMPLETED_WITHOUT_RESULT", "Agent 未返回最终结果");
            }
        }

        private void emit(RunEvent event) {
            RunEventEnvelope envelope = envelope(event);
            persistence.appendEvent(envelope);
            sink.next(envelope);
        }

        private RunEventEnvelope envelope(RunEvent event) {
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

        private void cleanup() {
            if (lockAcquired.compareAndSet(true, false)) {
                conversationLock.release(run.conversationId());
            }
            executor.release(run.conversationId());
        }
    }

    private static String valueOrRandom(String value) {
        return value == null || value.isBlank()
                ? UUID.randomUUID().toString()
                : value;
    }
}
