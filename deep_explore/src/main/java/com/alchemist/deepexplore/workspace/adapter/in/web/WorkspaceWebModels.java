package com.alchemist.deepexplore.workspace.adapter.in.web;

import com.alchemist.deepexplore.workspace.domain.RuntimeProfile;
import com.alchemist.deepexplore.workspace.domain.StarterTemplate;
import com.alchemist.deepexplore.workspace.domain.Workspace;
import com.alchemist.deepexplore.workspace.domain.WorkspaceEntry;
import com.alchemist.deepexplore.workspace.domain.WorkspaceStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

final class WorkspaceWebModels {

    private WorkspaceWebModels() {
    }

    record CreateRequest(
            @Size(max = 80) String name,
            RuntimeProfile runtimeProfile,
            StarterTemplate starterTemplate
    ) {
    }

    record WriteFileRequest(
            @NotBlank String path,
            @NotNull String content,
            String expectedRevision
    ) {
    }

    record CreateEntryRequest(
            String parentPath,
            @NotBlank @Size(max = 255) String name,
            @NotNull WorkspaceEntry.Type type
    ) {
    }

    record MoveEntryRequest(
            @NotBlank String sourcePath,
            @NotBlank String targetPath
    ) {
    }

    record CommandRequest(
            @NotBlank @Size(max = 8_000) String command,
            String workingDirectory
    ) {
    }

    record PreviewStartRequest(
            @NotBlank @Size(max = 8_000) String command,
            String workingDirectory,
            @Size(max = 500) String healthPath
    ) {
    }

    record Response(
            String id,
            String name,
            RuntimeProfile runtimeProfile,
            WorkspaceStatus status,
            String lastError,
            Instant createdAt,
            Instant updatedAt,
            Instant lastStartedAt
    ) {

        static Response from(Workspace workspace) {
            return new Response(
                    workspace.id(),
                    workspace.name(),
                    workspace.runtimeProfile(),
                    workspace.status(),
                    workspace.lastError(),
                    workspace.createdAt(),
                    workspace.updatedAt(),
                    workspace.lastStartedAt()
            );
        }
    }

    record ErrorResponse(String code, String message) {
    }
}
