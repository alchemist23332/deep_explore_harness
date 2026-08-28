package com.alchemist.deepexplore.harness.application.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.alchemist.deepexplore.harness.domain.AgentRun;
import com.alchemist.deepexplore.harness.domain.RunEvent;
import com.alchemist.deepexplore.harness.domain.RunEventEnvelope;
import com.alchemist.deepexplore.harness.domain.RunStatus;
import com.alchemist.deepexplore.harness.port.RunEventStore;
import com.alchemist.deepexplore.harness.port.RunStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class RunActivityQueryServiceTest {

    @Test
    void reconstructsSanitizedToolTimeline() {
        RunStore runStore = mock(RunStore.class);
        RunEventStore eventStore = mock(RunEventStore.class);
        Instant startedAt = Instant.parse("2026-08-17T02:00:00Z");
        AgentRun run = new AgentRun(
                "run-1",
                "conversation-1",
                "workspace-1",
                "user-1",
                "assistant-1",
                "assistant",
                "fast",
                RunStatus.COMPLETED,
                null,
                null,
                startedAt,
                startedAt,
                startedAt.plusSeconds(3)
        );
        when(runStore.listByConversation("conversation-1"))
                .thenReturn(List.of(run));
        when(eventStore.list("run-1", 0)).thenReturn(List.of(
                envelope(
                        1,
                        startedAt,
                        new RunEvent.RunStarted(
                                "fast",
                                "user-1",
                                "assistant-1",
                                "TAVILY"
                        )
                ),
                envelope(
                        2,
                        startedAt.plusSeconds(1),
                        new RunEvent.ToolCallStarted(
                                "tool-1",
                                "web_search",
                                "{\"query\":\"Java 21 latest news\"}"
                        )
                ),
                envelope(
                        3,
                        startedAt.plusSeconds(3),
                        new RunEvent.ToolCallCompleted(
                                "tool-1",
                                "web_search",
                                "raw result must stay server-side",
                                true
                        )
                )
        ));
        RunActivityQueryService service = new RunActivityQueryService(
                runStore,
                eventStore,
                new ToolActivityFormatter(new ObjectMapper())
        );

        List<RunActivityQueryService.RunActivityView> activities =
                service.list("conversation-1");

        assertThat(activities).singleElement().satisfies(activity -> {
            assertThat(activity.runId()).isEqualTo("run-1");
            assertThat(activity.workspaceId()).isEqualTo("workspace-1");
            assertThat(activity.tools()).singleElement().satisfies(tool -> {
                assertThat(tool.displayName()).isEqualTo("网页搜索");
                assertThat(tool.status()).isEqualTo(ToolStatus.SUCCEEDED);
                assertThat(tool.provider()).isEqualTo("TAVILY");
                assertThat(tool.durationMs()).isEqualTo(2_000);
                assertThat(tool.summary()).doesNotContain("Java 21 latest news");
            });
        });
    }

    private static RunEventEnvelope envelope(
            long sequence,
            Instant occurredAt,
            RunEvent event
    ) {
        return new RunEventEnvelope(
                "event-" + sequence,
                "run-1",
                "conversation-1",
                "assistant",
                sequence,
                occurredAt,
                event
        );
    }
}
