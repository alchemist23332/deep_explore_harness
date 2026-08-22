package com.alchemist.deepexplore.harness.application;

import com.alchemist.deepexplore.agent.domain.AgentExecutionEvent;
import com.alchemist.deepexplore.harness.domain.RunEvent;
import org.springframework.stereotype.Component;

@Component
public class AgentExecutionEventMapper {

    public RunEvent map(AgentExecutionEvent event) {
        return switch (event) {
            case AgentExecutionEvent.TextDelta delta ->
                    new RunEvent.TextDelta(delta.text());
            case AgentExecutionEvent.ToolCallStarted tool ->
                    new RunEvent.ToolCallStarted(
                            tool.toolCallId(),
                            tool.toolName(),
                            tool.argumentsJson()
                    );
            case AgentExecutionEvent.ToolCallCompleted tool ->
                    new RunEvent.ToolCallCompleted(
                            tool.toolCallId(),
                            tool.toolName(),
                            tool.result(),
                            tool.success()
                    );
            case AgentExecutionEvent.ApprovalRequired approval ->
                    new RunEvent.ApprovalRequired(
                            approval.approvalId(),
                            approval.prompt()
                    );
            case AgentExecutionEvent.ArtifactProduced artifact ->
                    new RunEvent.ArtifactProduced(
                            artifact.artifactId(),
                            artifact.kind(),
                            artifact.uri(),
                            artifact.metadata()
                    );
            case AgentExecutionEvent.Completed ignored ->
                    throw new IllegalArgumentException(
                            "Completed events are handled by RunSession"
                    );
            case AgentExecutionEvent.Failed ignored ->
                    throw new IllegalArgumentException(
                            "Failed events are handled by RunSession"
                    );
        };
    }
}
