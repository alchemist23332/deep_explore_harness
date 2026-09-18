package com.alchemist.deepexplore.harness.application.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.alchemist.deepexplore.agent.domain.AgentProfile;
import com.alchemist.deepexplore.agent.domain.WebSearchProvider;
import com.alchemist.deepexplore.config.WebSearchProperties;
import com.alchemist.deepexplore.harness.application.execution.HarnessService;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Flux;

class ChatStreamServiceTest {

    @Test
    void resolvesDefaultsAndMapsProfile() {
        HarnessService harness = mock(HarnessService.class);
        when(harness.start(any())).thenReturn(Flux.empty());
        ChatStreamService service = new ChatStreamService(
                harness,
                "assistant",
                properties()
        );

        ChatStreamService.ChatStream defaultStream = service.stream(
                command(AgentProfile.FAST, null)
        );
        ChatStreamService.ChatStream overrideStream = service.stream(
                command(AgentProfile.DEEP, WebSearchProvider.TAVILY)
        );

        ArgumentCaptor<StartRunCommand> commands =
                ArgumentCaptor.forClass(StartRunCommand.class);
        verify(harness, times(2)).start(commands.capture());
        assertThat(commands.getAllValues())
                .extracting(StartRunCommand::profileId)
                .containsExactly("fast", "deep");
        assertThat(commands.getAllValues())
                .extracting(StartRunCommand::searchProvider)
                .containsExactly(
                        WebSearchProvider.JINA,
                        WebSearchProvider.TAVILY
                );
        assertThat(commands.getAllValues())
                .extracting(StartRunCommand::workspaceId)
                .containsOnly("workspace-1");
        assertThat(defaultStream.searchProvider()).isEqualTo(WebSearchProvider.JINA);
        assertThat(overrideStream.searchProvider())
                .isEqualTo(WebSearchProvider.TAVILY);
    }

    private static ChatCommand command(
            AgentProfile profile,
            WebSearchProvider searchProvider
    ) {
        return new ChatCommand(
                "conversation-1",
                "hello",
                profile,
                "user-1",
                "parent-1",
                "assistant-1",
                searchProvider,
                "workspace-1"
        );
    }

    private static WebSearchProperties properties() {
        return new WebSearchProperties(
                true,
                WebSearchProvider.JINA,
                400,
                16_384,
                new WebSearchProperties.Jina(
                        "",
                        "https://s.jina.ai/",
                        Duration.ofSeconds(20),
                        30_000
                ),
                new WebSearchProperties.Tavily(
                        "",
                        "https://api.tavily.com/",
                        Duration.ofSeconds(10),
                        "basic",
                        false,
                        false
                )
        );
    }
}
