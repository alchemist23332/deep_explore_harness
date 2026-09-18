package com.alchemist.deepexplore.coding.adapter.in.langchain4j;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.alchemist.deepexplore.agent.application.AgentInvocationContextRegistry;
import com.alchemist.deepexplore.coding.application.CodingToolResult;
import com.alchemist.deepexplore.coding.application.CodingWorkspaceService;
import com.alchemist.deepexplore.coding.application.editing.TextEditOperation;
import com.alchemist.deepexplore.coding.config.CodingProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.alchemist.deepexplore.workspace.application.preview.PreviewApplicationService;
import com.alchemist.deepexplore.workspace.application.preview.PreviewStatus;
import com.alchemist.deepexplore.workspace.application.preview.PreviewView;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CodingToolsTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private AgentInvocationContextRegistry contexts;
    private CodingWorkspaceService workspaces;
    private PreviewApplicationService previews;
    private CodingTools tools;

    @BeforeEach
    void setUp() {
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
        contexts = new AgentInvocationContextRegistry();
        workspaces = mock(CodingWorkspaceService.class);
        previews = mock(PreviewApplicationService.class);
        tools = new CodingTools(
                contexts,
                workspaces,
                previews,
                properties,
                objectMapper
        );
    }

    @Test
    void routesToolToBoundWorkspace() throws Exception {
        contexts.bind("run-1", "conversation-1", "workspace-1");
        when(workspaces.readFile(
                "workspace-1",
                "src/App.java",
                1,
                20
        )).thenReturn(CodingToolResult.success(
                "Read src/App.java",
                java.util.Map.of("content", "1: class App {}")
        ));

        String result = tools.readFile(
                "run-1",
                "Inspect the application",
                "src/App.java",
                1,
                20
        );

        JsonNode json = objectMapper.readTree(result);
        assertThat(json.path("ok").asBoolean()).isTrue();
        assertThat(json.path("summary").asText())
                .isEqualTo("Read src/App.java");
        verify(workspaces).readFile(
                "workspace-1",
                "src/App.java",
                1,
                20
        );
    }

    @Test
    void returnsStructuredErrorWithoutWorkspaceBinding() throws Exception {
        String result = tools.runCommand(
                "run-1",
                "Run tests",
                "mvn test",
                ""
        );

        JsonNode json = objectMapper.readTree(result);
        assertThat(json.path("ok").asBoolean()).isFalse();
        assertThat(json.path("code").asText())
                .isEqualTo("WORKSPACE_NOT_BOUND");
    }

    @Test
    void routesExactEditOperationsToBoundWorkspace() throws Exception {
        contexts.bind("run-1", "conversation-1", "workspace-1");
        List<TextEditOperation> operations = List.of(new TextEditOperation(
                "oldValue",
                "newValue",
                false
        ));
        when(workspaces.editFile(
                "workspace-1",
                "src/App.java",
                "revision-1",
                operations
        )).thenReturn(CodingToolResult.success("Edited src/App.java", null));

        String result = tools.editFile(
                "run-1",
                "Rename value",
                "src/App.java",
                "revision-1",
                List.of(new CodingTools.EditOperationInput(
                        "oldValue",
                        "newValue",
                        false
                ))
        );

        assertThat(objectMapper.readTree(result).path("ok").asBoolean())
                .isTrue();
        verify(workspaces).editFile(
                "workspace-1",
                "src/App.java",
                "revision-1",
                operations
        );
    }

    @Test
    void doesNotApplyNormalCommandCallQuota()
            throws Exception {
        CodingProperties limited = new CodingProperties(
                true,
                32,
                12_000,
                32,
                200_000,
                12_000,
                200,
                20_000
        );
        tools = new CodingTools(
                contexts,
                workspaces,
                previews,
                limited,
                objectMapper
        );
        contexts.bind("run-1", "conversation-1", "workspace-1");
        when(workspaces.runCommand("workspace-1", "pwd", ""))
                .thenReturn(CodingToolResult.success("ok", null));
        String result = null;
        for (int index = 0; index < 8; index++) {
            result = tools.runCommand(
                    "run-1",
                    "Run command " + index,
                    "pwd",
                    ""
            );
        }

        JsonNode json = objectMapper.readTree(result);
        assertThat(json.path("ok").asBoolean()).isTrue();
        verify(workspaces, times(8))
                .runCommand("workspace-1", "pwd", "");
    }

    @Test
    void doesNotApplyNormalMutationCallQuota() throws Exception {
        contexts.bind("run-1", "conversation-1", "workspace-1");
        when(workspaces.writeFile(
                "workspace-1",
                "src/App.java",
                "class App {}",
                "revision-1"
        )).thenReturn(CodingToolResult.success("ok", null));

        String result = null;
        for (int index = 0; index < 11; index++) {
            result = tools.writeFile(
                    "run-1",
                    "Update app " + index,
                    "src/App.java",
                    "class App {}",
                    "revision-1"
            );
        }

        JsonNode json = objectMapper.readTree(result);
        assertThat(json.path("ok").asBoolean()).isTrue();
        verify(workspaces, times(11)).writeFile(
                "workspace-1",
                "src/App.java",
                "class App {}",
                "revision-1"
        );
    }

    @Test
    void emergencyFuseCountsFailuresAndRejectsBeforeCommandSideEffect()
            throws Exception {
        CodingProperties limited = new CodingProperties(
                true,
                32,
                12_000,
                32,
                200_000,
                12_000,
                200,
                20_000
        );
        tools = new CodingTools(
                contexts,
                workspaces,
                previews,
                limited,
                objectMapper
        );
        contexts.bind("run-1", "conversation-1", "workspace-1");
        when(workspaces.runCommand("workspace-1", "pwd", ""))
                .thenReturn(CodingToolResult.failure(
                        "command failed",
                        "COMMAND_FAILED"
                ));
        for (int index = 0; index < 32; index++) {
            tools.runCommand("run-1", "Attempt " + index, "pwd", "");
        }

        String result = tools.runCommand(
                "run-1",
                "Rejected attempt",
                "pwd",
                ""
        );

        JsonNode json = objectMapper.readTree(result);
        assertThat(json.path("ok").asBoolean()).isFalse();
        assertThat(json.path("code").asText())
                .isEqualTo("EMERGENCY_TOOL_CALL_LIMIT_EXCEEDED");
        verify(workspaces, times(32))
                .runCommand("workspace-1", "pwd", "");
    }

    @Test
    void startsPreviewForBoundWorkspace() throws Exception {
        contexts.bind("run-1", "conversation-1", "workspace-1");
        when(previews.start(
                "workspace-1",
                "pnpm dev --host 0.0.0.0 --port 3000",
                "",
                "/"
        )).thenReturn(new PreviewView(
                PreviewStatus.RUNNING,
                "http://127.0.0.1:49152",
                3000,
                49_152,
                "ready"
        ));

        String result = tools.startPreview(
                "run-1",
                "Start the game",
                "pnpm dev --host 0.0.0.0 --port 3000",
                "",
                "/"
        );

        JsonNode json = objectMapper.readTree(result);
        assertThat(json.path("ok").asBoolean()).isTrue();
        assertThat(json.at("/data/url").asText())
                .isEqualTo("http://127.0.0.1:49152");
    }
}
