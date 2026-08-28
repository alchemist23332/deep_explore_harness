package com.alchemist.deepexplore.coding.adapter.in.langchain4j;

import com.alchemist.deepexplore.agent.application.AgentInvocationContextRegistry;
import com.alchemist.deepexplore.coding.application.CodingToolException;
import com.alchemist.deepexplore.coding.application.CodingToolResult;
import com.alchemist.deepexplore.coding.application.CodingWorkspaceService;
import com.alchemist.deepexplore.coding.config.CodingProperties;
import com.alchemist.deepexplore.workspace.application.WorkspaceOperationException;
import com.alchemist.deepexplore.workspace.application.preview.PreviewApplicationService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolMemoryId;
import java.util.function.Function;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "tools.coding",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class CodingTools {

    private final AgentInvocationContextRegistry contexts;
    private final CodingWorkspaceService workspaces;
    private final PreviewApplicationService previews;
    private final CodingProperties properties;
    private final ObjectMapper objectMapper;

    public CodingTools(
            AgentInvocationContextRegistry contexts,
            CodingWorkspaceService workspaces,
            PreviewApplicationService previews,
            CodingProperties properties,
            ObjectMapper objectMapper
    ) {
        this.contexts = contexts;
        this.workspaces = workspaces;
        this.previews = previews;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Tool(
            name = "list_files",
            value = "List files and directories in the active workspace. "
                    + "Paths are relative to the workspace root."
    )
    public String listFiles(
            @ToolMemoryId String conversationId,
            @P(name = "description", description = "Why the directory is being inspected")
            String description,
            @P(name = "path", description = "Workspace-relative directory path", required = false)
            String path,
            @P(name = "depth", description = "Recursive depth from 0 to 5", required = false)
            Integer depth,
            @P(name = "limit", description = "Maximum entries to return", required = false)
            Integer limit
    ) {
        return invoke(conversationId, Operation.READ, context ->
                workspaces.listFiles(
                        context.workspaceId(),
                        path,
                        depth,
                        limit
                ));
    }

    @Tool(
            name = "read_file",
            value = "Read UTF-8 text from a file in the active workspace. "
                    + "Returns numbered lines and a revision for safe edits."
    )
    public String readFile(
            @ToolMemoryId String conversationId,
            @P(name = "description", description = "Why the file is being read")
            String description,
            @P(name = "path", description = "Workspace-relative file path")
            String path,
            @P(name = "startLine", description = "1-based first line", required = false)
            Integer startLine,
            @P(name = "endLine", description = "1-based inclusive last line", required = false)
            Integer endLine
    ) {
        return invoke(conversationId, Operation.READ, context ->
                workspaces.readFile(
                        context.workspaceId(),
                        path,
                        startLine,
                        endLine
                ));
    }

    @Tool(
            name = "grep_search",
            value = "Search file contents with ripgrep in the active workspace."
    )
    public String grepSearch(
            @ToolMemoryId String conversationId,
            @P(name = "description", description = "Why the code search is needed")
            String description,
            @P(name = "pattern", description = "Text or regular expression to search")
            String pattern,
            @P(name = "path", description = "Workspace-relative directory path", required = false)
            String path,
            @P(name = "glob", description = "Optional glob such as *.java", required = false)
            String glob,
            @P(name = "caseSensitive", description = "Whether matching is case-sensitive", required = false)
            Boolean caseSensitive,
            @P(name = "limit", description = "Maximum matching lines", required = false)
            Integer limit
    ) {
        return invoke(conversationId, Operation.READ, context ->
                workspaces.grepSearch(
                        context.workspaceId(),
                        pattern,
                        path,
                        glob,
                        caseSensitive,
                        limit
                ));
    }

    @Tool(
            name = "write_file",
            value = "Create or fully replace a UTF-8 file in the active "
                    + "workspace. Existing files require the revision returned "
                    + "by read_file."
    )
    public String writeFile(
            @ToolMemoryId String conversationId,
            @P(name = "description", description = "Why the file is being written")
            String description,
            @P(name = "path", description = "Workspace-relative file path")
            String path,
            @P(name = "content", description = "Complete new file content")
            String content,
            @P(name = "expectedRevision", description = "Revision returned by read_file", required = false)
            String expectedRevision
    ) {
        return invoke(conversationId, Operation.MUTATION, context ->
                workspaces.writeFile(
                        context.workspaceId(),
                        path,
                        content,
                        expectedRevision
                ));
    }

    @Tool(
            name = "apply_patch",
            value = "Apply a unified diff to the active workspace. Patch "
                    + "headers must use workspace-relative a/ and b/ paths."
    )
    public String applyPatch(
            @ToolMemoryId String conversationId,
            @P(name = "description", description = "Why this patch is needed")
            String description,
            @P(name = "patch", description = "Unified diff patch")
            String patch
    ) {
        return invoke(conversationId, Operation.MUTATION, context ->
                workspaces.applyPatch(context.workspaceId(), patch));
    }

    @Tool(
            name = "run_command",
            value = "Run a shell command inside the active Docker workspace "
                    + "and return its exit code and bounded output. Use an "
                    + "empty workingDirectory for the workspace root; never "
                    + "pass /workspace."
    )
    public String runCommand(
            @ToolMemoryId String conversationId,
            @P(name = "description", description = "Why the command is being run")
            String description,
            @P(name = "command", description = "Shell command to execute")
            String command,
            @P(name = "workingDirectory", description = "Workspace-relative directory, or empty for the root; never /workspace", required = false)
            String workingDirectory
    ) {
        return invoke(conversationId, Operation.COMMAND, context ->
                workspaces.runCommand(
                        context.workspaceId(),
                        command,
                        workingDirectory
                ));
    }

    @Tool(
            name = "start_preview",
            value = "Start or replace the long-running web preview for the "
                    + "active workspace. The server must listen on "
                    + "0.0.0.0:3000. Use an empty workingDirectory for the "
                    + "workspace root; never pass /workspace."
    )
    public String startPreview(
            @ToolMemoryId String conversationId,
            @P(name = "description", description = "Why the preview is being started")
            String description,
            @P(name = "command", description = "Foreground command that starts the web server")
            String command,
            @P(name = "workingDirectory", description = "Workspace-relative directory, or empty for the root; never /workspace", required = false)
            String workingDirectory,
            @P(name = "healthPath", description = "HTTP path used for readiness checks", required = false)
            String healthPath
    ) {
        return invoke(conversationId, Operation.COMMAND, context ->
                CodingToolResult.success(
                        "Preview is running",
                        previews.start(
                                context.workspaceId(),
                                command,
                                workingDirectory,
                                healthPath
                        )
                ));
    }

    @Tool(
            name = "preview_status",
            value = "Inspect the active workspace web preview status and URL."
    )
    public String previewStatus(
            @ToolMemoryId String conversationId,
            @P(name = "description", description = "Why preview status is being checked")
            String description
    ) {
        return invoke(conversationId, Operation.READ, context ->
                CodingToolResult.success(
                        "Preview status checked",
                        previews.status(context.workspaceId())
                ));
    }

    @Tool(
            name = "preview_logs",
            value = "Read bounded logs from the active workspace web preview."
    )
    public String previewLogs(
            @ToolMemoryId String conversationId,
            @P(name = "description", description = "Why preview logs are being read")
            String description
    ) {
        return invoke(conversationId, Operation.READ, context ->
                CodingToolResult.success(
                        "Preview logs read",
                        java.util.Map.of(
                                "logs",
                                previews.logs(context.workspaceId())
                        )
                ));
    }

    @Tool(
            name = "stop_preview",
            value = "Stop the active workspace web preview."
    )
    public String stopPreview(
            @ToolMemoryId String conversationId,
            @P(name = "description", description = "Why the preview is being stopped")
            String description
    ) {
        return invoke(conversationId, Operation.COMMAND, context ->
                CodingToolResult.success(
                        "Preview stopped",
                        previews.stop(context.workspaceId())
                ));
    }

    private String invoke(
            String conversationId,
            Operation operation,
            Function<AgentInvocationContextRegistry.Context, CodingToolResult>
                    action
    ) {
        try {
            AgentInvocationContextRegistry.Context context =
                    contexts.find(conversationId).orElseThrow(() ->
                            new CodingToolException(
                                    "WORKSPACE_NOT_BOUND",
                                    "No workspace is bound to this Agent run"
                            ));
            consume(context, operation);
            return json(action.apply(context));
        } catch (CodingToolException error) {
            return json(CodingToolResult.failure(
                    error.getMessage(),
                    error.code()
            ));
        } catch (WorkspaceOperationException error) {
            return json(CodingToolResult.failure(
                    error.getMessage(),
                    error.code()
            ));
        } catch (RuntimeException error) {
            return json(CodingToolResult.failure(
                    "Coding tool execution failed",
                    "TOOL_EXECUTION_FAILED"
            ));
        }
    }

    private void consume(
            AgentInvocationContextRegistry.Context context,
            Operation operation
    ) {
        requireWithinLimit(
                context.increment("tools"),
                properties.maxToolCalls(),
                "TOOL_BUDGET_EXCEEDED",
                "Coding tool call budget exceeded"
        );
        if (operation == Operation.MUTATION) {
            requireWithinLimit(
                    context.increment("mutations"),
                    properties.maxMutationCalls(),
                    "MUTATION_BUDGET_EXCEEDED",
                    "Coding mutation budget exceeded"
            );
        }
        if (operation == Operation.COMMAND) {
            requireWithinLimit(
                    context.increment("commands"),
                    properties.maxCommandCalls(),
                    "COMMAND_BUDGET_EXCEEDED",
                    "Coding command budget exceeded"
            );
        }
    }

    private static void requireWithinLimit(
            int actual,
            int maximum,
            String code,
            String message
    ) {
        if (actual > maximum) {
            throw new CodingToolException(code, message);
        }
    }

    private String json(CodingToolResult result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException(
                    "Unable to serialize coding tool result",
                    error
            );
        }
    }

    private enum Operation {
        READ,
        MUTATION,
        COMMAND
    }
}
