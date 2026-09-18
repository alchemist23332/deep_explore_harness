package com.alchemist.deepexplore.coding.adapter.in.langchain4j;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.alchemist.deepexplore.agent.application.AgentInvocationContextRegistry;
import com.alchemist.deepexplore.agent.adapter.langchain4j.tool.execution.ToolEffect;
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
                        512,
                        12_000,
                        32,
                        200_000,
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

        var providedTools = provider.provideTools(
                request("run-1")
        ).aiServiceTools();
        assertThat(providedTools)
                .hasSize(10)
                .extracting(tool -> tool.toolSpecification().name())
                .containsExactlyInAnyOrder(
                        "list_files",
                        "read_file",
                        "grep_search",
                        "write_file",
                        "edit_file",
                        "run_command",
                        "start_preview",
                        "preview_status",
                        "preview_logs",
                        "stop_preview"
                );
        assertThat(providedTools).filteredOn(tool ->
                tool.toolSpecification().name().equals("edit_file")
        ).singleElement().satisfies(tool -> assertThat(
                tool.toolSpecification().toJson()
        ).contains(
                "expectedRevision",
                "edits",
                "oldText",
                "newText",
                "replaceAll"
        ));
        assertThat(provider.toolPolicies()).hasSize(10);
        assertThat(provider.toolPolicies().get("read_file").effect())
                .isEqualTo(ToolEffect.READ_ONLY);
        assertThat(provider.toolPolicies().get("write_file").effect())
                .isEqualTo(ToolEffect.WORKSPACE_MUTATION);
        assertThat(provider.toolPolicies().get("edit_file").effect())
                .isEqualTo(ToolEffect.WORKSPACE_MUTATION);
        assertThat(provider.toolPolicies()).doesNotContainKey("apply_patch");
        assertThat(provider.toolDescriptors())
                .extracting(descriptor -> descriptor.name())
                .contains("edit_file", "apply_patch");
        assertThat(provider.toolPolicies().get("run_command").effect())
                .isEqualTo(ToolEffect.EXCLUSIVE);
    }

    private static ToolProviderRequest request(String conversationId) {
        return new ToolProviderRequest(
                conversationId,
                UserMessage.from("change the code")
        );
    }
}
