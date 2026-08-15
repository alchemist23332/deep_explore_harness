package com.alchemist.deepexplore.agent.application;

import com.alchemist.deepexplore.agent.AgentCommand;
import com.alchemist.deepexplore.agent.AgentEvent;
import com.alchemist.deepexplore.agent.AgentMode;
import com.alchemist.deepexplore.agent.AgentService;
import com.alchemist.deepexplore.agent.adapter.langchain4j.LangChain4jAgentExecutor;
import com.alchemist.deepexplore.harness.application.HarnessService;
import com.alchemist.deepexplore.harness.application.StartRunCommand;
import com.alchemist.deepexplore.harness.domain.RunEvent;
import com.alchemist.deepexplore.harness.domain.RunEventEnvelope;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
public class AgentServiceFacade implements AgentService {

    private final HarnessService harness;
    private final String defaultAgentId;

    public AgentServiceFacade(
            HarnessService harness,
            @Value("${ai.agent.id:assistant}") String defaultAgentId
    ) {
        this.harness = harness;
        this.defaultAgentId = defaultAgentId;
    }

    @Override
    public Flux<AgentEvent> stream(AgentCommand command) {
        StartRunCommand startRun = new StartRunCommand(
                command.conversationId(),
                command.message(),
                defaultAgentId,
                profileId(command.mode()),
                command.userMessageId(),
                command.userParentMessageId(),
                command.assistantMessageId()
        );
        return harness.start(startRun).mapNotNull(this::toLegacyEvent);
    }

    private AgentEvent toLegacyEvent(RunEventEnvelope envelope) {
        return switch (envelope.event()) {
            case RunEvent.RunStarted ignored ->
                    AgentEvent.metadata(envelope.conversationId());
            case RunEvent.TextDelta delta ->
                    AgentEvent.delta(envelope.conversationId(), delta.text());
            case RunEvent.ToolCallStarted tool ->
                    new AgentEvent(
                            "tool_start",
                            envelope.conversationId(),
                            tool.toolName()
                    );
            case RunEvent.ToolCallCompleted tool ->
                    new AgentEvent(
                            "tool_end",
                            envelope.conversationId(),
                            tool.resultJson()
                    );
            case RunEvent.ApprovalRequired approval ->
                    new AgentEvent(
                            "approval_required",
                            envelope.conversationId(),
                            approval.prompt()
                    );
            case RunEvent.ArtifactProduced artifact ->
                    new AgentEvent(
                            "artifact",
                            envelope.conversationId(),
                            artifact.uri()
                    );
            case RunEvent.RunCompleted ignored ->
                    AgentEvent.done(envelope.conversationId());
            case RunEvent.RunFailed failed ->
                    AgentEvent.error(envelope.conversationId(), failed.message());
            case RunEvent.RunCancelled ignored ->
                    AgentEvent.error(envelope.conversationId(), "生成已取消");
            case RunEvent.CheckpointSaved ignored -> null;
        };
    }

    private static String profileId(AgentMode mode) {
        return mode == AgentMode.DEEP
                ? LangChain4jAgentExecutor.DEEP_PROFILE
                : LangChain4jAgentExecutor.FAST_PROFILE;
    }
}
