package com.alchemist.deepexplore.harness.application.query;

import com.alchemist.deepexplore.harness.domain.AgentRun;
import com.alchemist.deepexplore.harness.domain.RunEvent;
import com.alchemist.deepexplore.harness.domain.RunEventEnvelope;
import com.alchemist.deepexplore.harness.domain.RunStatus;
import com.alchemist.deepexplore.harness.port.RunEventStore;
import com.alchemist.deepexplore.harness.port.RunStore;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class RunActivityQueryService {

    private final RunStore runStore;
    private final RunEventStore eventStore;
    private final ToolActivityFormatter toolFormatter;

    public RunActivityQueryService(
            RunStore runStore,
            RunEventStore eventStore,
            ToolActivityFormatter toolFormatter
    ) {
        this.runStore = runStore;
        this.eventStore = eventStore;
        this.toolFormatter = toolFormatter;
    }

    public List<RunActivityView> list(String conversationId) {
        return runStore.listByConversation(conversationId).stream()
                .map(this::toActivity)
                .filter(activity -> !activity.tools().isEmpty())
                .toList();
    }

    private RunActivityView toActivity(AgentRun run) {
        List<RunEventEnvelope> events = eventStore.list(run.id(), 0);
        Map<String, MutableToolActivity> tools = new LinkedHashMap<>();
        String provider = provider(events);

        for (RunEventEnvelope envelope : events) {
            switch (envelope.event()) {
                case RunEvent.ToolCallStarted started ->
                        tools.put(started.toolCallId(), new MutableToolActivity(
                                started.toolCallId(),
                                started.toolName(),
                                toolFormatter.displayName(started.toolName()),
                                ToolStatus.RUNNING,
                                toolFormatter.startedSummary(
                                        started.toolName(),
                                        started.argumentsJson(),
                                        provider
                                ),
                                provider,
                                envelope.occurredAt(),
                                null
                        ));
                case RunEvent.ToolCallCompleted completed ->
                        completeTool(tools, completed, envelope.occurredAt());
                default -> {
                }
            }
        }

        tools.values().stream()
                .filter(tool -> tool.status == ToolStatus.RUNNING)
                .forEach(tool -> completeUnfinished(tool, run));

        return new RunActivityView(
                run.id(),
                run.assistantMessageId(),
                run.workspaceId(),
                run.status().name(),
                tools.values().stream()
                        .map(MutableToolActivity::toView)
                        .toList()
        );
    }

    private void completeTool(
            Map<String, MutableToolActivity> tools,
            RunEvent.ToolCallCompleted completed,
            Instant completedAt
    ) {
        MutableToolActivity activity = tools.get(completed.toolCallId());
        if (activity == null) {
            return;
        }
        activity.status = completed.success()
                ? ToolStatus.SUCCEEDED
                : ToolStatus.FAILED;
        activity.summary = toolFormatter.completedSummary(
                completed.toolName(),
                completed.result(),
                completed.success()
        );
        activity.completedAt = completedAt;
    }

    private void completeUnfinished(MutableToolActivity tool, AgentRun run) {
        if (run.status() == RunStatus.RUNNING
                || run.status() == RunStatus.WAITING_APPROVAL) {
            return;
        }
        tool.status = run.status() == RunStatus.CANCELLED
                ? ToolStatus.CANCELLED
                : ToolStatus.FAILED;
        tool.summary = run.status() == RunStatus.CANCELLED
                ? toolFormatter.displayName(tool.toolName) + "已取消"
                : toolFormatter.completedSummary(tool.toolName, false);
        tool.completedAt = run.completedAt();
    }

    private static String provider(List<RunEventEnvelope> events) {
        return events.stream()
                .map(RunEventEnvelope::event)
                .filter(RunEvent.RunStarted.class::isInstance)
                .map(RunEvent.RunStarted.class::cast)
                .map(RunEvent.RunStarted::searchProvider)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(null);
    }

    public record RunActivityView(
            String runId,
            String assistantMessageId,
            String workspaceId,
            String status,
            List<ToolActivityView> tools
    ) {
    }

    public record ToolActivityView(
            String toolCallId,
            String toolName,
            String displayName,
            ToolStatus status,
            String summary,
            String provider,
            Instant startedAt,
            Instant completedAt,
            Long durationMs
    ) {
    }

    private static final class MutableToolActivity {

        private final String toolCallId;
        private final String toolName;
        private final String displayName;
        private ToolStatus status;
        private String summary;
        private final String provider;
        private final Instant startedAt;
        private Instant completedAt;

        private MutableToolActivity(
                String toolCallId,
                String toolName,
                String displayName,
                ToolStatus status,
                String summary,
                String provider,
                Instant startedAt,
                Instant completedAt
        ) {
            this.toolCallId = toolCallId;
            this.toolName = toolName;
            this.displayName = displayName;
            this.status = status;
            this.summary = summary;
            this.provider = provider;
            this.startedAt = startedAt;
            this.completedAt = completedAt;
        }

        private ToolActivityView toView() {
            return new ToolActivityView(
                    toolCallId,
                    toolName,
                    displayName,
                    status,
                    summary,
                    provider,
                    startedAt,
                    completedAt,
                    durationMillis()
            );
        }

        private Long durationMillis() {
            return startedAt == null || completedAt == null
                    ? null
                    : Math.max(
                            0,
                            Duration.between(startedAt, completedAt).toMillis()
                    );
        }
    }
}
