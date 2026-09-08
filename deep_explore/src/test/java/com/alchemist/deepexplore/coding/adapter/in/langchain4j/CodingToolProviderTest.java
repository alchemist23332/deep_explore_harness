package com.alchemist.deepexplore.coding.adapter.in.langchain4j;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.alchemist.deepexplore.agent.application.AgentInvocationContextRegistry;
import com.alchemist.deepexplore.coding.application.CodingWorkspaceService;
import com.alchemist.deepexplore.coding.config.CodingProperties;
import com.alchemist.deepexplore.workspace.application.preview.PreviewApplicationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.service.tool.ToolProviderRequest;
import org.junit.jupiter.api.Test;

class CodingToolProviderTest {

    @Test
    void exposesCodingToolsOnlyForBoundWorkspace() {
        AgentInvocationContextRegistry contexts =
                new AgentInvocationContextRegistry();
        CodingProperties properties = new CodingProperties(
                        true,
                        20,
                        10,
                        5,
                        12_000,
                        12_000,
                        200,
                        20_000
                );
        CodingTools tools = new CodingTools(
                contexts,
                mock(CodingWorkspaceService.class),
                mock(PreviewApplicationService.class),
                properties,
                new ObjectMapper()
        );
        CodingToolProvider provider = new CodingToolProvider(contexts, tools);

        assertThat(provider.isDynamic()).isFalse();
        assertThat(provider.provideTools(
                request("run-1")
        ).aiServiceTools())
                .isEmpty();

        contexts.bind("run-1", "conversation-1", "workspace-1");

        assertThat(provider.provideTools(
                request("run-1")
        ).aiServiceTools())
                .hasSize(10)
                .extracting(tool -> tool.toolSpecification().name())
                .containsExactlyInAnyOrder(
                        "list_files",
                        "read_file",
                        "grep_search",
                        "write_file",
                        "apply_patch",
                        "run_command",
                        "start_preview",
                        "preview_status",
                        "preview_logs",
                        "stop_preview"
                );
    }

    private static ToolProviderRequest request(String conversationId) {
        return new ToolProviderRequest(
                conversationId,
                UserMessage.from("change the code")
        );
    }
}
