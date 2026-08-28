package com.alchemist.deepexplore.harness.adapter.in.web;

import com.alchemist.deepexplore.harness.application.query.ToolActivityFormatter;
import com.alchemist.deepexplore.harness.application.query.ToolStatus;
import com.alchemist.deepexplore.harness.domain.RunEvent;
import com.alchemist.deepexplore.harness.domain.RunEventEnvelope;
import org.springframework.stereotype.Component;

@Component
public class ChatEventPresenter {

    private final ToolActivityFormatter toolFormatter;

    public ChatEventPresenter(ToolActivityFormatter toolFormatter) {
        this.toolFormatter = toolFormatter;
    }

    public ChatStreamEvent present(
            RunEventEnvelope envelope,
            String searchProvider
    ) {
        return switch (envelope.event()) {
            case RunEvent.RunStarted started ->
                    event(envelope, "metadata", "", started.assistantMessageId(), null);
            case RunEvent.TextDelta delta ->
                    event(envelope, "delta", delta.text(), null, null);
            case RunEvent.ToolCallStarted tool -> {
                String summary = toolFormatter.startedSummary(
                        tool.toolName(),
                        tool.argumentsJson(),
                        searchProvider
                );
                yield event(
                        envelope,
                        "tool_start",
                        summary,
                        null,
                        new ChatStreamEvent.ToolActivity(
                                tool.toolCallId(),
                                tool.toolName(),
                                toolFormatter.displayName(tool.toolName()),
                                ToolStatus.RUNNING,
                                summary,
                                searchProvider
                        )
                );
            }
            case RunEvent.ToolCallCompleted tool -> {
                String summary = toolFormatter.completedSummary(
                        tool.toolName(),
                        tool.result(),
                        tool.success()
                );
                yield event(
                        envelope,
                        "tool_end",
                        summary,
                        null,
                        new ChatStreamEvent.ToolActivity(
                                tool.toolCallId(),
                                tool.toolName(),
                                toolFormatter.displayName(tool.toolName()),
                                tool.success()
                                        ? ToolStatus.SUCCEEDED
                                        : ToolStatus.FAILED,
                                summary,
                                searchProvider
                        )
                );
            }
            case RunEvent.ApprovalRequired approval ->
                    event(envelope, "approval_required", approval.prompt(), null, null);
            case RunEvent.ArtifactProduced artifact ->
                    event(envelope, "artifact", artifact.uri(), null, null);
            case RunEvent.RunCompleted completed ->
                    event(envelope, "done", "", completed.assistantMessageId(), null);
            case RunEvent.RunFailed failed ->
                    event(envelope, "error", failed.message(), null, null);
            case RunEvent.RunCancelled ignored ->
                    event(envelope, "error", "生成已取消", null, null);
            case RunEvent.CheckpointSaved ignored -> null;
        };
    }

    private static ChatStreamEvent event(
            RunEventEnvelope envelope,
            String type,
            String content,
            String assistantMessageId,
            ChatStreamEvent.ToolActivity tool
    ) {
        return new ChatStreamEvent(
                type,
                envelope.conversationId(),
                content,
                envelope.runId(),
                envelope.sequence(),
                envelope.occurredAt(),
                assistantMessageId,
                tool
        );
    }
}
