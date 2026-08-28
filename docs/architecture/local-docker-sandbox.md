# Local Docker Sandbox

## Scope

The local runtime provides a browser-managed Fullstack workspace with Java 21,
Maven, Node.js 22, npm, pnpm, tmux, and ripgrep. The browser and Coding Agent
can edit the same persistent files, execute bounded commands, and run one web
preview per workspace.

Cloud multi-tenancy, a public Preview Gateway, desktop GUI forwarding, and
production deployment remain deferred.

## Ownership and Storage

The local deployment uses the fixed owner `local-user`. One owner can create
multiple workspaces. Each workspace has:

- PostgreSQL metadata in `workspaces`
- persistent files under `.deep-explore-data/workspaces/{id}/files`
- a persistent Maven cache under `.deep-explore-data/workspaces/{id}/cache/m2`
- persistent npm and pnpm caches under the workspace cache directory
- at most one disposable Docker container

The container is compute state, not the source of truth. Stopping or replacing
it does not remove workspace files.

The backend keeps the workspace as a module inside the modular monolith:

```text
workspace/
  domain/                   Workspace and file value types
  application/command/      Bounded command execution
  application/lifecycle/    Create, start, stop, delete, operation locking
  application/query/        Workspace and runtime-profile queries
  application/preview/      Preview process lifecycle and readiness
  application/terminal/     PTY session lifecycle
  port/                     Storage, template, watcher, runtime interfaces
  adapter/out/filesystem/   NIO files, ZIP import, templates, WatchService
  adapter/out/docker/       Container, PTY, and tmux preview runtime
  adapter/out/http/         Preview readiness probe
  adapter/out/postgres/     Workspace metadata
```

Application services depend on ports and contain no NIO, ZIP, or
`WatchService` implementation details. ArchUnit enforces that boundary.

Workspace creation accepts `WEB_TYPESCRIPT`, `JAVA_MAVEN`, or `EMPTY`.
`WEB_TYPESCRIPT` is the default and installs a minimal Vite project. Templates
are installed only during creation; reading an intentionally emptied workspace
never recreates deleted files.

## Runtime

`sandbox/java21/Dockerfile` builds
`deep-explore/sandbox-fullstack:v1`. The container runs as UID/GID 1000 with
all Linux capabilities dropped, `no-new-privileges`, CPU, memory, and PID
limits. The managed workspace plus Maven, npm, and pnpm caches are mounted.

Container port `3000` is published on a random loopback-only host port. Preview
servers must listen on `0.0.0.0:3000`. The runtime version label forces legacy
containers to be recreated with the port binding while preserving their
workspace files.

The local MVP uses bridge networking so Maven and Gradle can download
dependencies. This is not a public multi-tenant security boundary. A hosted
version must move the Docker adapter to an isolated sandbox node and enforce
egress policy.

On macOS the project uses Colima for the Linux VM. The Docker CLI connects to
Colima's Unix socket, while docker-java uses a loopback-only `socat` bridge at
`tcp://127.0.0.1:23750` because its Apache HTTP transport does not reliably
connect to Colima's Unix socket on this environment. The bridge is managed by
`scripts/docker-runtime.sh` and is never exposed on a non-loopback interface.

## API

```text
GET    /api/runtime-profiles
GET    /api/workspaces
POST   /api/workspaces
GET    /api/workspaces/{id}
POST   /api/workspaces/{id}/start
POST   /api/workspaces/{id}/stop
DELETE /api/workspaces/{id}
GET    /api/workspaces/{id}/files
GET    /api/workspaces/{id}/tree
GET    /api/workspaces/{id}/file
PUT    /api/workspaces/{id}/file
POST   /api/workspaces/{id}/entries
PATCH  /api/workspaces/{id}/entries
DELETE /api/workspaces/{id}/entries
POST   /api/workspaces/{id}/upload
POST   /api/workspaces/{id}/import/zip
POST   /api/workspaces/{id}/commands
POST   /api/workspaces/{id}/preview/start
GET    /api/workspaces/{id}/preview
GET    /api/workspaces/{id}/preview/logs
POST   /api/workspaces/{id}/preview/stop
GET    /api/workspaces/{id}/events       (SSE)
WS     /api/workspaces/{id}/terminal
```

Workspace paths are normalized under the managed root. Absolute paths,
traversal, and symbolic-link traversal are rejected. ZIP imports enforce
compressed size, extracted size, and file-count limits.

Text file responses include a SHA-256 `revision`. Conditional writes compare
the supplied revision before replacing a file and return
`WORKSPACE_FILE_CHANGED` on stale edits. The browser editor submits this
revision when saving, preventing silent overwrites after an Agent or terminal
changes the same file.

## Unified Workbench

The frontend presents chat and sandbox operations in one workbench rather than
as separate pages:

- the existing conversation sidebar remains on the left
- chat occupies the center panel
- the sandbox occupies a resizable right panel on desktop
- the header button opens or closes the sandbox without leaving the chat
- the sandbox can be maximized and restored
- screens up to 820 px use a full-screen drawer instead of a compressed split
  layout

`WorkbenchSandboxProvider` owns presentation state independently from the
conversation runtime and workspace API state. It persists panel visibility,
desktop panel size, active workspace, active tab, and the selected file for
each workspace in `localStorage`. The sandbox panel itself owns transient
server state such as the workspace list, runtime availability, unsaved editor
state, and command progress.

Uploads complete only after the root file tree has reloaded. Uploaded text
files are selected immediately, while unsupported binary previews remain
visible in the tree and return an explicit preview error.

The Explorer uses `react-arborist` with a server-backed controlled tree. It
supports nested expansion, creation, inline rename, recursive deletion,
drag-and-drop moves, keyboard operations, persisted open state, and multiple
editor tabs. A Java `WatchService` observes the persistent workspace directory
recursively and publishes SSE events, so files created by terminal commands
also refresh the Explorer. Watchers are reference-counted and close when the
last SSE subscriber disconnects or the workspace is deleted.

The user terminal uses `@xterm/xterm` over a WebSocket-backed Docker exec PTY.
Input, ANSI output, terminal resize, and control keys travel through the
WebSocket. Terminal sessions are limited per workspace and are closed when the
socket disconnects or the workspace stops or is deleted. The existing
`POST /commands` endpoint remains a bounded, non-interactive command API for
future Agent tools.

`/chat` is the canonical workbench route. The former `/workspaces` and
`/workspaces/{id}` routes remain as compatibility redirects that open the
sandbox in the unified workbench.

## Preview Runtime

Long-running web servers do not use the bounded command endpoint. The Docker
preview adapter writes a controlled script inside the container and runs it in
the `deep-explore-preview` tmux session. Logs and the exit code remain under
`/tmp/deep-explore-preview`; start waits for an HTTP response through the
published host port before returning `RUNNING`.

The frontend Preview tab polls status, embeds the URL in a sandboxed iframe,
and provides reload, open, logs, and stop controls. This local URL is suitable
only for the current machine. A hosted deployment requires an authenticated,
expiring Preview Gateway instead of exposing Docker host ports directly.

## Coding Agent Integration

Runs with a `workspaceId` receive ten workspace-scoped tools:

```text
WorkspaceStorage        -> list_files, read_file, write_file
WorkspaceCommandService -> grep_search, apply_patch, run_command
PreviewApplication      -> start_preview, preview_status, preview_logs,
                           stop_preview
```

Tool paths are always relative to `/workspace`; the host storage path is never
part of the model contract. An empty `workingDirectory` means the workspace
root; absolute paths are rejected. Mutations, commands, and preview lifecycle
operations share the workspace operation lock. Finite commands remain bounded
by the Docker runtime timeout and output limit; long-running preview commands
are isolated in tmux. Full tool results are supplied to the model, while
browser and database Run Events retain only sanitized summaries.

Approval, resumable tool execution, change journals, rollback, and multi-agent
workflow graphs remain deferred.
