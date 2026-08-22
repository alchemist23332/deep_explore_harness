# Local Docker Sandbox

## Scope

Phase one provides a browser-managed Java 21 workspace. It is intentionally
independent from Conversation, Agent, and Harness. The browser can create a
workspace, upload or edit files, start and stop its container, and execute
commands manually.

Agent tools, conversation binding, checkpoints, approvals, and cloud
multi-tenancy are deferred.

## Ownership and Storage

The local deployment uses the fixed owner `local-user`. One owner can create
multiple workspaces. Each workspace has:

- PostgreSQL metadata in `workspaces`
- persistent files under `.deep-explore-data/workspaces/{id}/files`
- a persistent Maven cache under `.deep-explore-data/workspaces/{id}/cache/m2`
- at most one disposable Docker container

The container is compute state, not the source of truth. Stopping or replacing
it does not remove workspace files.

New Java 21 workspaces are initialized from the resource template under
`workspace-templates/java21/`. The template contains a runnable Maven project
with `pom.xml`, application and test sources, `README.md`, and `.gitignore`.
Initialization is idempotent: an empty legacy workspace is backfilled when it
is next loaded, while any workspace that already contains files is left
untouched.

## Runtime

`sandbox/java21/Dockerfile` builds
`deep-explore/sandbox-java21:v1`. The container runs as UID/GID 1000 with all
Linux capabilities dropped, `no-new-privileges`, CPU, memory, and PID limits.
Only the managed workspace and Maven cache directories are mounted.

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
GET    /api/workspaces/{id}/events       (SSE)
WS     /api/workspaces/{id}/terminal
```

Workspace paths are normalized under the managed root. Absolute paths,
traversal, and symbolic-link traversal are rejected. ZIP imports enforce
compressed size, extracted size, and file-count limits.

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
also refresh the Explorer.

The user terminal uses `@xterm/xterm` over a WebSocket-backed Docker exec PTY.
Input, ANSI output, terminal resize, and control keys travel through the
WebSocket. Terminal sessions are limited per workspace and are closed when the
socket disconnects or the workspace stops or is deleted. The existing
`POST /commands` endpoint remains a bounded, non-interactive command API for
future Agent tools.

`/chat` is the canonical workbench route. The former `/workspaces` and
`/workspaces/{id}` routes remain as compatibility redirects that open the
sandbox in the unified workbench.

## Future Harness Integration

The existing services become the backing implementation for future tools:

```text
WorkspaceFileService    -> list_files, read_file, apply_patch
WorkspaceApplicationService.execute -> run_command, run_tests
SandboxRuntime          -> workspace lifecycle
```

That integration should add workspace ownership to conversations and runs,
workspace-level locking, asynchronous command events, diff/checkpoint storage,
and approval policy. None of those concerns are embedded in the phase-one
runtime API.
