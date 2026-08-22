package com.alchemist.deepexplore.harness.adapter.in.web;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.alchemist.deepexplore.harness.application.RunActivityQueryService;
import com.alchemist.deepexplore.harness.application.RunActivityQueryService.RunActivityView;
import com.alchemist.deepexplore.harness.application.RunActivityQueryService.ToolActivityView;
import com.alchemist.deepexplore.harness.application.ToolStatus;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;

class RunActivityControllerTest {

    @Test
    void returnsApplicationReadModel() {
        RunActivityQueryService service = mock(RunActivityQueryService.class);
        when(service.list("conversation-1")).thenReturn(List.of(
                new RunActivityView(
                        "run-1",
                        "assistant-1",
                        "COMPLETED",
                        List.of(new ToolActivityView(
                                "tool-1",
                                "web_search",
                                "网页搜索",
                                ToolStatus.SUCCEEDED,
                                "搜索完成，正在整理结果",
                                "TAVILY",
                                Instant.EPOCH,
                                Instant.EPOCH.plusSeconds(2),
                                2_000L
                        ))
                )
        ));

        WebTestClient.bindToController(new RunActivityController(service))
                .build()
                .get()
                .uri("/api/conversations/conversation-1/run-activities")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].runId").isEqualTo("run-1")
                .jsonPath("$[0].tools[0].status").isEqualTo("SUCCEEDED")
                .jsonPath("$[0].tools[0].durationMs").isEqualTo(2_000);
    }
}
