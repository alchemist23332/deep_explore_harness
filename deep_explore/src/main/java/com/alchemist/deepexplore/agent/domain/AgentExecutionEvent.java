package com.alchemist.deepexplore.agent.domain;

import java.util.Map;

public sealed interface AgentExecutionEvent {

    record TextDelta(String text) implements AgentExecutionEvent {
    }

    record ToolCallStarted(
            String toolCallId,
            String toolName,
            String argumentsJson
    ) implements AgentExecutionEvent {
    }

    record ToolCallCompleted(
            String toolCallId,
            String toolName,
            String resultJson,
            boolean success
    ) implements AgentExecutionEvent {
    }

    record ApprovalRequired(
            String approvalId,
            String prompt
    ) implements AgentExecutionEvent {
    }

    record ArtifactProduced(
            String artifactId,
            String kind,
            String uri,
            Map<String, Object> metadata
    ) implements AgentExecutionEvent {
    }

    record Completed(
            String text,
            String model,
            Integer tokenUsage
    ) implements AgentExecutionEvent {
    }

    record Failed(
            String code,
            String message
    ) implements AgentExecutionEvent {
    }
}
