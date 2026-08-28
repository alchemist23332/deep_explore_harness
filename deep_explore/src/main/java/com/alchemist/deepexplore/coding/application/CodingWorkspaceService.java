package com.alchemist.deepexplore.coding.application;

import com.alchemist.deepexplore.coding.config.CodingProperties;
import com.alchemist.deepexplore.workspace.application.WorkspaceOperationException;
import com.alchemist.deepexplore.workspace.application.command.WorkspaceCommandService;
import com.alchemist.deepexplore.workspace.application.lifecycle.WorkspaceLifecycleService;
import com.alchemist.deepexplore.workspace.application.lifecycle.WorkspaceOperationCoordinator;
import com.alchemist.deepexplore.workspace.application.query.WorkspaceQueryService;
import com.alchemist.deepexplore.workspace.domain.CommandResult;
import com.alchemist.deepexplore.workspace.domain.WorkspaceEntry;
import com.alchemist.deepexplore.workspace.domain.WorkspaceFile;
import com.alchemist.deepexplore.workspace.domain.WorkspaceStatus;
import com.alchemist.deepexplore.workspace.port.WorkspaceStorage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class CodingWorkspaceService {

    private static final int MAX_DIRECTORY_DEPTH = 5;
    private static final int MAX_COMMAND_CHARACTERS = 8_000;
    private static final Pattern PATCH_PATH = Pattern.compile(
            "^(---|\\+\\+\\+)\\s+([^\\t\\r\\n]+)",
            Pattern.MULTILINE
    );

    private final WorkspaceQueryService workspaces;
    private final WorkspaceLifecycleService lifecycle;
    private final WorkspaceStorage storage;
    private final WorkspaceCommandService commands;
    private final WorkspaceOperationCoordinator operations;
    private final CodingProperties properties;

    public CodingWorkspaceService(
            WorkspaceQueryService workspaces,
            WorkspaceLifecycleService lifecycle,
            WorkspaceStorage storage,
            WorkspaceCommandService commands,
            WorkspaceOperationCoordinator operations,
            CodingProperties properties
    ) {
        this.workspaces = workspaces;
        this.lifecycle = lifecycle;
        this.storage = storage;
        this.commands = commands;
        this.operations = operations;
        this.properties = properties;
    }

    public CodingToolResult listFiles(
            String workspaceId,
            String path,
            Integer depth,
            Integer limit
    ) {
        requireWorkspace(workspaceId);
        String root = normalizePath(path);
        int safeDepth = Math.max(
                0,
                Math.min(depth == null ? 2 : depth, MAX_DIRECTORY_DEPTH)
        );
        int safeLimit = Math.max(
                1,
                Math.min(
                        limit == null ? properties.maxSearchResults() : limit,
                        properties.maxSearchResults()
                )
        );
        List<String> entries = new ArrayList<>();
        collectEntries(
                workspaceId,
                root,
                safeDepth,
                safeLimit,
                entries
        );
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("path", root);
        data.put("entries", entries);
        data.put("truncated", entries.size() >= safeLimit);
        return CodingToolResult.success(
                "Listed " + entries.size() + " workspace entries",
                data
        );
    }

    public CodingToolResult readFile(
            String workspaceId,
            String path,
            Integer startLine,
            Integer endLine
    ) {
        requireWorkspace(workspaceId);
        WorkspaceFile file = storage.read(workspaceId, path);
        String[] lines = file.content().split("\\n", -1);
        int start = startLine == null ? 1 : startLine;
        int end = endLine == null ? lines.length : endLine;
        if (start < 1 || end < start || start > lines.length) {
            throw new CodingToolException(
                    "INVALID_LINE_RANGE",
                    "Requested line range is outside the file"
            );
        }
        end = Math.min(end, lines.length);
        StringBuilder content = new StringBuilder();
        boolean truncated = false;
        for (int index = start - 1; index < end; index++) {
            String line = (index + 1) + ": " + lines[index] + "\n";
            if (content.length() + line.length()
                    > properties.maxReadCharacters()) {
                truncated = true;
                break;
            }
            content.append(line);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("path", file.path());
        data.put("revision", file.revision());
        data.put("startLine", start);
        data.put("endLine", end);
        data.put("totalLines", lines.length);
        data.put("truncated", truncated);
        data.put("content", content.toString());
        return CodingToolResult.success(
                "Read " + file.path() + " lines " + start + "-" + end,
                data
        );
    }

    public CodingToolResult grepSearch(
            String workspaceId,
            String pattern,
            String path,
            String glob,
            Boolean caseSensitive,
            Integer limit
    ) {
        if (pattern == null || pattern.isBlank()) {
            throw new CodingToolException(
                    "INVALID_PATTERN",
                    "Search pattern must not be blank"
            );
        }
        ensureRunning(workspaceId);
        int safeLimit = Math.max(
                1,
                Math.min(
                        limit == null ? properties.maxSearchResults() : limit,
                        properties.maxSearchResults()
                )
        );
        StringBuilder command = new StringBuilder(
                "rg --line-number --no-heading --color never --max-columns 500"
        );
        if (!Boolean.TRUE.equals(caseSensitive)) {
            command.append(" --ignore-case");
        }
        if (glob != null && !glob.isBlank()) {
            command.append(" --glob ").append(shellQuote(glob));
        }
        command.append(" -- ")
                .append(shellQuote(pattern))
                .append(" ")
                .append(shellQuote(normalizePath(path).isBlank()
                        ? "."
                        : normalizePath(path)));
        CommandResult result = commands.execute(workspaceId, command.toString(), "");
        if (result.exitCode() != null
                && result.exitCode() != 0
                && result.exitCode() != 1) {
            throw commandFailure("GREP_FAILED", "Code search failed", result);
        }
        List<String> matches = result.stdout().lines()
                .limit(safeLimit)
                .toList();
        String content = truncate(
                String.join("\n", matches),
                properties.maxSearchCharacters()
        );
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("pattern", pattern);
        data.put("path", normalizePath(path));
        data.put("matches", content);
        data.put("shownMatches", matches.size());
        data.put(
                "truncated",
                result.truncated()
                        || result.stdout().lines().count() > safeLimit
                        || content.length() < String.join("\n", matches).length()
        );
        return CodingToolResult.success(
                "Found " + matches.size() + " matching lines",
                data
        );
    }

    public CodingToolResult writeFile(
            String workspaceId,
            String path,
            String content,
            String expectedRevision
    ) {
        requireWorkspace(workspaceId);
        return operations.withLock(workspaceId, () -> {
            boolean exists = true;
            try {
                storage.read(workspaceId, path);
            } catch (WorkspaceOperationException error) {
                if (!"WORKSPACE_FILE_NOT_FOUND".equals(error.code())) {
                    throw error;
                }
                exists = false;
            }
            if (exists
                    && (expectedRevision == null
                    || expectedRevision.isBlank())) {
                throw new CodingToolException(
                        "EXPECTED_REVISION_REQUIRED",
                        "Read the existing file before overwriting it"
                );
            }
            WorkspaceFile written = storage.write(
                    workspaceId,
                    path,
                    content == null ? "" : content,
                    expectedRevision
            );
            return CodingToolResult.success(
                    "Wrote " + written.path(),
                    Map.of(
                            "path", written.path(),
                            "size", written.size(),
                            "revision", written.revision()
                    )
            );
        });
    }

    public CodingToolResult applyPatch(String workspaceId, String patch) {
        requireWorkspace(workspaceId);
        List<String> changedPaths = validatePatch(patch);
        return operations.withLock(workspaceId, () -> {
            String patchPath = ".deep-explore-patch-"
                    + UUID.randomUUID() + ".diff";
            storage.write(workspaceId, patchPath, patch);
            try {
                String patchArgument = shellQuote(patchPath);
                CommandResult result = executeInRunningWorkspace(
                        workspaceId,
                        "git apply --check --whitespace=nowarn -- "
                                + patchArgument
                                + " && git apply --whitespace=nowarn -- "
                                + patchArgument,
                        ""
                );
                if (result.exitCode() == null || result.exitCode() != 0) {
                    throw commandFailure(
                            "PATCH_APPLY_FAILED",
                            "Patch could not be applied",
                            result
                    );
                }
                return CodingToolResult.success(
                        "Applied patch to " + changedPaths.size() + " file(s)",
                        Map.of("changedFiles", changedPaths)
                );
            } finally {
                try {
                    storage.deleteEntry(workspaceId, patchPath, false);
                } catch (WorkspaceOperationException ignored) {
                    // The patch result is more important than temporary cleanup.
                }
            }
        });
    }

    public CodingToolResult runCommand(
            String workspaceId,
            String command,
            String workingDirectory
    ) {
        if (command == null || command.isBlank()) {
            throw new CodingToolException(
                    "INVALID_COMMAND",
                    "Command must not be blank"
            );
        }
        if (command.length() > MAX_COMMAND_CHARACTERS) {
            throw new CodingToolException(
                    "COMMAND_TOO_LONG",
                    "Command exceeds the coding command size limit"
            );
        }
        CommandResult result = executeInRunningWorkspace(
                workspaceId,
                command,
                normalizePath(workingDirectory)
        );
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("command", result.command());
        data.put("exitCode", result.exitCode());
        data.put(
                "stdout",
                truncate(
                        result.stdout(),
                        properties.maxCommandResultCharacters()
                )
        );
        data.put(
                "stderr",
                truncate(
                        result.stderr(),
                        properties.maxCommandResultCharacters()
                )
        );
        data.put("durationMs", result.durationMs());
        data.put("timedOut", result.timedOut());
        data.put("truncated", result.truncated());
        if (result.exitCode() == null || result.exitCode() != 0) {
            return CodingToolResult.failure(
                    result.timedOut()
                            ? "Command timed out"
                            : "Command failed with exit code "
                                    + result.exitCode(),
                    result.timedOut()
                            ? "COMMAND_TIMED_OUT"
                            : "COMMAND_FAILED",
                    data
            );
        }
        return CodingToolResult.success("Command completed successfully", data);
    }

    private void collectEntries(
            String workspaceId,
            String path,
            int depth,
            int limit,
            List<String> entries
    ) {
        if (entries.size() >= limit) {
            return;
        }
        for (WorkspaceEntry entry : storage.list(workspaceId, path)) {
            if (entries.size() >= limit) {
                return;
            }
            entries.add(
                    entry.type() == WorkspaceEntry.Type.DIRECTORY
                            ? entry.path() + "/"
                            : entry.path()
            );
            if (entry.type() == WorkspaceEntry.Type.DIRECTORY && depth > 0) {
                collectEntries(
                        workspaceId,
                        entry.path(),
                        depth - 1,
                        limit,
                        entries
                );
            }
        }
    }

    private CommandResult executeInRunningWorkspace(
            String workspaceId,
            String command,
            String workingDirectory
    ) {
        ensureRunning(workspaceId);
        return commands.execute(workspaceId, command, workingDirectory);
    }

    private void ensureRunning(String workspaceId) {
        var workspace = workspaces.get(workspaceId);
        if (workspace.status() != WorkspaceStatus.RUNNING) {
            lifecycle.start(workspaceId);
        }
    }

    private void requireWorkspace(String workspaceId) {
        workspaces.get(workspaceId);
    }

    private static List<String> validatePatch(String patch) {
        if (patch == null || patch.isBlank()) {
            throw new CodingToolException(
                    "INVALID_PATCH",
                    "Patch must not be blank"
            );
        }
        List<String> changedPaths = new ArrayList<>();
        Matcher matcher = PATCH_PATH.matcher(patch);
        while (matcher.find()) {
            String raw = matcher.group(2).strip();
            if ("/dev/null".equals(raw)) {
                continue;
            }
            String path = raw.startsWith("a/") || raw.startsWith("b/")
                    ? raw.substring(2)
                    : raw;
            if (path.isBlank()
                    || path.startsWith("/")
                    || path.startsWith("../")
                    || path.contains("/../")
                    || path.startsWith(".deep-explore-patch-")
                    || path.indexOf('\\') >= 0) {
                throw new CodingToolException(
                        "INVALID_PATCH_PATH",
                        "Patch paths must stay within the workspace"
                );
            }
            if (!changedPaths.contains(path)) {
                changedPaths.add(path);
            }
        }
        if (changedPaths.isEmpty()) {
            throw new CodingToolException(
                    "INVALID_PATCH",
                    "Patch does not contain a target file"
            );
        }
        return List.copyOf(changedPaths);
    }

    private static CodingToolException commandFailure(
            String code,
            String message,
            CommandResult result
    ) {
        String details = result.stderr().isBlank()
                ? result.stdout()
                : result.stderr();
        return new CodingToolException(
                code,
                message + ": " + truncate(details, 2_000)
        );
    }

    private static String normalizePath(String path) {
        return path == null ? "" : path.strip();
    }

    private static String shellQuote(String value) {
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }

    private static String truncate(String value, int maximum) {
        if (value == null || value.length() <= maximum) {
            return value == null ? "" : value;
        }
        return value.substring(0, maximum)
                + "\n... [truncated "
                + (value.length() - maximum)
                + " chars]";
    }
}
