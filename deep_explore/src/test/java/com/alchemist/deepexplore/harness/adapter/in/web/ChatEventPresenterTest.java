package com.alchemist.deepexplore.harness.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.alchemist.deepexplore.harness.application.query.ToolActivityFormatter;
import com.alchemist.deepexplore.harness.application.query.ToolStatus;
import com.alchemist.deepexplore.harness.domain.RunEvent;
import com.alchemist.deepexplore.harness.domain.RunEventEnvelope;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ChatEventPresenterTest {

    private final ChatEventPresenter presenter = new ChatEventPresenter(
            new ToolActivityFormatter(new ObjectMapper())
    );

    @Test
    void exposesSanitizedToolLifecycle() {
        ChatStreamEvent started = presenter.present(
                envelope(
                        1,
                        new RunEvent.ToolCallStarted(
                                "tool-1",
                                "web_search",
                                "{\"query\":\"Java 21 latest news\"}"
                        )
                ),
                "TAVILY"
        );
        ChatStreamEvent completed = presenter.present(
                envelope(
                        2,
                        new RunEvent.ToolCallCompleted(
                                "tool-1",
                                "web_search",
                                "raw result must not reach the browser",
                                true
                        )
                ),
                "TAVILY"
        );

        assertThat(started.type()).isEqualTo("tool_start");
        assertThat(started.tool().status()).isEqualTo(ToolStatus.RUNNING);
        assertThat(started.tool().summary())
                .contains("Tavily")
                .contains("Java 21 latest news");
        assertThat(completed.type()).isEqualTo("tool_end");
        assertThat(completed.tool().status()).isEqualTo(ToolStatus.SUCCEEDED);
        assertThat(completed.content())
                .isEqualTo("搜索完成，正在整理结果")
                .doesNotContain("raw result");
    }

    @Test
    void exposesCodingDescriptionAndStructuredResultSummary() {
        ChatStreamEvent started = presenter.present(
                envelope(
                        1,
                        new RunEvent.ToolCallStarted(
                                "tool-1",
                                "read_file",
                                "{\"description\":\"检查应用入口\","
                                        + "\"path\":\"src/App.java\"}"
                        )
                ),
                null
        );
        ChatStreamEvent completed = presenter.present(
                envelope(
                        2,
                        new RunEvent.ToolCallCompleted(
                                "tool-1",
                                "read_file",
                                "{\"ok\":true,\"summary\":"
                                        + "\"Read src/App.java\"}",
                                true
                        )
                ),
                null
        );

        assertThat(started.tool().displayName()).isEqualTo("读取文件");
        assertThat(started.tool().summary()).isEqualTo("检查应用入口");
        assertThat(completed.tool().summary())
                .isEqualTo("Read src/App.java");
    }

    private static RunEventEnvelope envelope(long sequence, RunEvent event) {
        return new RunEventEnvelope(
                "event-" + sequence,
                "run-1",
                "conversation-1",
                "assistant",
                sequence,
                Instant.EPOCH.plusSeconds(sequence),
                event
        );
    }
}
