package com.alchemist.deepexplore.workspace.adapter.in.web;

import com.alchemist.deepexplore.workspace.application.WorkspaceApplicationService;
import com.alchemist.deepexplore.workspace.application.WorkspaceChangeService;
import com.alchemist.deepexplore.workspace.application.WorkspaceFileService;
import com.alchemist.deepexplore.workspace.domain.CommandResult;
import com.alchemist.deepexplore.workspace.domain.WorkspaceChange;
import com.alchemist.deepexplore.workspace.domain.WorkspaceEntry;
import com.alchemist.deepexplore.workspace.domain.WorkspaceFile;
import com.alchemist.deepexplore.workspace.domain.WorkspaceTreeNode;
import jakarta.validation.Valid;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

@RestController
@RequestMapping("/api")
public class WorkspaceController {

    private final WorkspaceApplicationService workspaces;
    private final WorkspaceFileService files;
    private final WorkspaceChangeService changes;

    public WorkspaceController(
            WorkspaceApplicationService workspaces,
            WorkspaceFileService files,
            WorkspaceChangeService changes
    ) {
        this.workspaces = workspaces;
        this.files = files;
        this.changes = changes;
    }

    @GetMapping("/runtime-profiles")
    public Mono<WorkspaceApplicationService.RuntimeOverview> runtimeProfiles() {
        return blocking(workspaces::runtimeOverview);
    }

    @GetMapping("/workspaces")
    public Mono<List<WorkspaceWebModels.Response>> list() {
        return blocking(() -> workspaces.list().stream()
                .map(WorkspaceWebModels.Response::from)
                .toList());
    }

    @PostMapping("/workspaces")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<WorkspaceWebModels.Response> create(
            @Valid @RequestBody WorkspaceWebModels.CreateRequest request
    ) {
        return blocking(() -> WorkspaceWebModels.Response.from(
                workspaces.create(request.name(), request.runtimeProfile())
        ));
    }

    @GetMapping("/workspaces/{workspaceId}")
    public Mono<WorkspaceWebModels.Response> get(
            @PathVariable String workspaceId
    ) {
        return blocking(() -> WorkspaceWebModels.Response.from(
                workspaces.get(workspaceId)
        ));
    }

    @PostMapping("/workspaces/{workspaceId}/start")
    public Mono<WorkspaceWebModels.Response> start(
            @PathVariable String workspaceId
    ) {
        return blocking(() -> WorkspaceWebModels.Response.from(
                workspaces.start(workspaceId)
        ));
    }

    @PostMapping("/workspaces/{workspaceId}/stop")
    public Mono<WorkspaceWebModels.Response> stop(
            @PathVariable String workspaceId
    ) {
        return blocking(() -> WorkspaceWebModels.Response.from(
                workspaces.stop(workspaceId)
        ));
    }

    @DeleteMapping("/workspaces/{workspaceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> delete(@PathVariable String workspaceId) {
        return blocking(() -> {
            workspaces.delete(workspaceId);
            return (Void) null;
        });
    }

    @GetMapping("/workspaces/{workspaceId}/files")
    public Mono<List<WorkspaceEntry>> listFiles(
            @PathVariable String workspaceId,
            @RequestParam(defaultValue = "") String path
    ) {
        return blocking(() -> {
            workspaces.get(workspaceId);
            return files.list(workspaceId, path);
        });
    }

    @GetMapping("/workspaces/{workspaceId}/tree")
    public Mono<List<WorkspaceTreeNode>> tree(
            @PathVariable String workspaceId
    ) {
        return blocking(() -> {
            workspaces.get(workspaceId);
            return files.tree(workspaceId);
        });
    }

    @PostMapping("/workspaces/{workspaceId}/entries")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<WorkspaceEntry> createEntry(
            @PathVariable String workspaceId,
            @Valid @RequestBody WorkspaceWebModels.CreateEntryRequest request
    ) {
        return blocking(() -> {
            workspaces.get(workspaceId);
            return files.create(
                    workspaceId,
                    request.parentPath(),
                    request.name(),
                    request.type()
            );
        });
    }

    @org.springframework.web.bind.annotation.PatchMapping(
            "/workspaces/{workspaceId}/entries"
    )
    public Mono<WorkspaceEntry> moveEntry(
            @PathVariable String workspaceId,
            @Valid @RequestBody WorkspaceWebModels.MoveEntryRequest request
    ) {
        return blocking(() -> {
            workspaces.get(workspaceId);
            return files.move(
                    workspaceId,
                    request.sourcePath(),
                    request.targetPath()
            );
        });
    }

    @DeleteMapping("/workspaces/{workspaceId}/entries")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> deleteEntry(
            @PathVariable String workspaceId,
            @RequestParam String path,
            @RequestParam(defaultValue = "false") boolean recursive
    ) {
        return blocking(() -> {
            workspaces.get(workspaceId);
            files.delete(workspaceId, path, recursive);
            return (Void) null;
        });
    }

    @GetMapping(
            value = "/workspaces/{workspaceId}/events",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE
    )
    public Flux<ServerSentEvent<WorkspaceChange>> events(
            @PathVariable String workspaceId
    ) {
        return blocking(() -> workspaces.get(workspaceId))
                .thenMany(changes.changes(workspaceId))
                .map(change -> ServerSentEvent.builder(change)
                        .event("workspace_change")
                        .build());
    }

    @GetMapping("/workspaces/{workspaceId}/file")
    public Mono<WorkspaceFile> readFile(
            @PathVariable String workspaceId,
            @RequestParam String path
    ) {
        return blocking(() -> {
            workspaces.get(workspaceId);
            return files.read(workspaceId, path);
        });
    }

    @PutMapping("/workspaces/{workspaceId}/file")
    public Mono<WorkspaceFile> writeFile(
            @PathVariable String workspaceId,
            @Valid @RequestBody WorkspaceWebModels.WriteFileRequest request
    ) {
        return blocking(() -> {
            workspaces.get(workspaceId);
            return files.write(
                    workspaceId,
                    request.path(),
                    request.content()
            );
        });
    }

    @PostMapping(
            value = "/workspaces/{workspaceId}/upload",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public Mono<WorkspaceFileService.UploadResult> uploadFile(
            @PathVariable String workspaceId,
            @RequestPart("file") FilePart file,
            @RequestParam(required = false) String path
    ) {
        String destination = path == null || path.isBlank()
                ? file.filename()
                : path;
        return withTemporaryUpload(file, temporary -> {
            workspaces.get(workspaceId);
            try (var input = Files.newInputStream(temporary)) {
                return files.upload(
                        workspaceId,
                        destination,
                        input,
                        Files.size(temporary)
                );
            }
        });
    }

    @PostMapping(
            value = "/workspaces/{workspaceId}/import/zip",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public Mono<WorkspaceFileService.ImportResult> importZip(
            @PathVariable String workspaceId,
            @RequestPart("file") FilePart file
    ) {
        return withTemporaryUpload(file, temporary -> {
            workspaces.get(workspaceId);
            try (var input = Files.newInputStream(temporary)) {
                return files.importZip(
                        workspaceId,
                        input,
                        Files.size(temporary)
                );
            }
        });
    }

    @PostMapping("/workspaces/{workspaceId}/commands")
    public Mono<CommandResult> execute(
            @PathVariable String workspaceId,
            @Valid @RequestBody WorkspaceWebModels.CommandRequest request
    ) {
        return blocking(() -> workspaces.execute(
                workspaceId,
                request.command(),
                request.workingDirectory()
        ));
    }

    private static <T> Mono<T> withTemporaryUpload(
            FilePart file,
            ThrowingFunction<Path, T> action
    ) {
        return Mono.fromCallable(
                        () -> Files.createTempFile(
                                "deep-explore-upload-",
                                ".tmp"
                        )
                )
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(temporary -> file.transferTo(temporary)
                        .then(blocking(() -> action.apply(temporary)))
                        .doFinally(signal -> deleteQuietly(temporary)));
    }

    private static <T> Mono<T> blocking(Callable<T> action) {
        return Mono.fromCallable(action)
                .subscribeOn(Schedulers.boundedElastic());
    }

    private static void deleteQuietly(Path temporary) {
        try {
            Files.deleteIfExists(temporary);
        } catch (IOException ignored) {
            // Temporary upload cleanup is best effort.
        }
    }

    @FunctionalInterface
    private interface ThrowingFunction<T, R> {

        R apply(T value) throws Exception;
    }
}
