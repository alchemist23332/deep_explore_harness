# Agent Harness Architecture

## Status

Implemented in the phase 1-4 refactor.

## Decision

Deep Explore remains a modular monolith. The backend is separated into three
business modules and infrastructure adapters:

```text
conversation/
  domain/                   Conversation and user-visible message history
  application/              Conversation use cases
  port/                     Conversation, message, and lock interfaces
  adapter/in/web/           HTTP API
  adapter/out/postgres/     JDBC implementations

agent/
  domain/                   Framework-neutral execution requests and events
  application/              Executor registry
  spi/                      AgentExecutor and capability extension points
  adapter/langchain4j/      LangChain4j implementation and ChatMemory

harness/
  domain/                   Run, event, status, and checkpoint types
  application/command/      Chat commands and streaming facade
  application/execution/    Run orchestration, sessions, context, persistence
  application/query/        Run activity and tool timeline queries
  adapter/in/web/           Chat SSE and Run activity HTTP APIs
  port/                     Run, event, and checkpoint persistence interfaces
  adapter/out/postgres/     PostgreSQL implementations

runtime/
  application/              Sanitized runtime capability query
  adapter/in/web/           Runtime configuration HTTP API

coding/
  application/              Workspace-scoped coding operations and results
  adapter/in/langchain4j/    Invocation-scoped Tool provider and prompt policy
```

The dependency direction is:

```text
Web -> Application -> Domain/Port <- Infrastructure Adapter
Harness -> Agent SPI
Harness -> Conversation Ports
Coding -> Agent application context
Coding -> Workspace application/ports
Conversation -X-> Agent/Harness
Agent -X-> Conversation
Application -X-> Adapter
Input Adapter -X-> Output Port/Adapter
Domain -X-> Spring/JDBC/LangChain4j/Reactor
```

ArchUnit tests enforce these rules.

## Runtime Flow

1. `RunController` maps the request to a framework-neutral `ChatCommand`.
2. `RunExecutionManager` starts the Harness independently of the SSE
   subscriber and returns the durable `runId`. `ChatController` remains a
   compatibility facade.
3. `ConversationContextLoader` opens the conversation and `RunFactory` creates
   the `AgentRun` and `RunSession`; the Harness emits `RunStarted`.
4. The Harness acquires an owner-scoped conversation lease, then
   `ConversationContextLoader` reads canonical history through Conversation
   ports. `RunSession` renews the lease while the Run is active.
5. The Agent adapter prepares ChatMemory from the supplied history when needed,
   and the snapshot is persisted as a checkpoint.
6. The user message is appended to complete conversation history.
7. `AgentExecutorRegistry` selects an executor by `agentId`.
8. `LangChain4jAgentExecutor` starts the AI Services ReAct loop.
   The AI Services `@MemoryId` is the Run ID, while the memory provider resolves
   it back to the Conversation ID for persistent ChatMemory. Tool invocation
   state is therefore isolated per Run.
9. When a Run carries a `workspaceId`, the Tool provider exposes
   workspace-scoped coding and preview tools and adds the coding prompt
   fragment. The provider is evaluated once per invocation because the
   workspace binding does not change during a Run.
10. When required, the model calls the provider-neutral `web_search` Tool.
   `@ToolMemoryId` routes the call to Jina or Tavily for this task, then
   LangChain4j stores the result in ChatMemory and invokes the model again.
11. Tool and text events are converted to framework-neutral execution events.
    The HTTP facade emits sanitized `tool_start` and `tool_end` SSE payloads;
    raw arguments and results are not exposed to the browser.
12. PostgreSQL atomically assigns event sequence numbers. All lifecycle, tool,
    text, approval, artifact, and terminal events are persisted before live
    publication, allowing replay from `afterSequence`.
13. `RunSession` owns terminal transitions, cancellation, lease renewal,
    Executor cleanup, and ordered event envelopes.
14. Completion writes the assistant message and closes the Run.
15. Failure or cancellation marks provider ChatMemory dirty. The next Run
    rebuilds it from canonical `messages`, including the retained user message.
    Checkpoints remain available for a future explicit resume flow.
16. Executor state and the conversation lease are released with the current
    Run owner ID and lease version, so stale cleanup cannot clear a newer Run.

The ReAct loop is intentionally delegated to LangChain4j AI Services. The
Harness owns the outer durable Run lifecycle and does not duplicate provider
tool-call parsing or result-message construction.

## Compatibility

The frontend uses `POST /api/runs`, `GET /api/runs/{runId}/events`, and
`POST /api/runs/{runId}/cancel`. SSE disconnection only removes an observer;
it does not cancel the Run. `POST /api/chat/stream` remains available as a
compatibility facade.

The frontend renders tool activity as an assistant-ui `data` message part.
`GET /api/conversations/{conversationId}/run-activities` reconstructs the same
sanitized timeline from persisted Run Events when conversation history reloads.
Each activity also carries its persisted `workspaceId`. When a conversation is
reopened, the workbench selects the most recent workspace used to modify or
preview the project; each execution timeline also provides an explicit action
to open its associated workspace.

## Persistence

V1 remains the source for user-visible history:

- `conversations`
- `messages`
- `conversation_memory`

V2 adds execution state:

- `agent_runs`
- `agent_run_events`
- `run_checkpoints`

Conversation history and Agent runtime state are intentionally separate. A
single user message can produce multiple Runs when regenerated.

`messages` remains the canonical user-visible history. The Harness maps its
latest branch to framework-neutral `AgentMessage` values. The LangChain4j
adapter may persist provider-specific ChatMemory in `conversation_memory`, but
it no longer reads Conversation stores directly. A persistent `dirty` marker
forces reconstruction from canonical history after failed or cancelled Runs.

Each coding Run stores its nullable `workspace_id`. The Agent invocation
context binds that Run to one workspace while it is active; Coding Tools never
accept host paths and resolve all paths relative to that workspace. The
binding is removed on completion, failure, cancellation, and executor release.

## Coding Tools

Coding runs expose `list_files`, `read_file`, `grep_search`, `write_file`,
`apply_patch`, `run_command`, `start_preview`, `preview_status`,
`preview_logs`, and `stop_preview`. File operations use `WorkspaceStorage`;
finite commands execute only through the Docker-backed
`WorkspaceCommandService`. Preview commands run in a managed tmux session and
must listen on `0.0.0.0:3000`. Existing-file writes require the revision
returned by `read_file`, while patch application uses `git apply --check`
before mutation.

Tool results returned to the model are structured and bounded. Run events keep
only the tool summary and error code, avoiding persistence of source files and
full command output.

## Prompt Composition

System prompts are XML-structured Markdown resources under
`src/main/resources/prompts`. `SystemPromptRenderer` combines the common
assistant policy, the selected profile definition, and prompt fragments
contributed by enabled capabilities. It validates fragment IDs, ordering,
resource availability, and XML roots before the assistant bean is available.

Tool configuration remains in `application.yml`, but tool behavior policies do
not. An enabled tool registers a `SystemPromptContributor`; disabling the tool
removes both its LangChain4j tool bean and its policy from the rendered system
message. `AiModelConfig` only injects the rendered document through
LangChain4j's `systemMessageProvider` and contains no tool-specific prompt text.
`ToolDescriptorRegistry` owns display metadata and event exposure rules;
`CompositeToolProvider` discovers tool providers without central switches.

## Extension Rules

To add an Agent:

1. Implement `AgentExecutor`.
2. Return a unique `agentId`.
3. Register it as a Spring bean.
4. Send that `agentId` in `StartRunCommand`.

To add a tool or approval flow:

1. Register the LangChain4j tool in the Agent adapter.
2. Add its XML-Markdown policy under `resources/prompts/tools`.
3. Register a conditional `SystemPromptContributor` with the tool.
4. Map tool lifecycle callbacks to typed `AgentExecutionEvent` values.
5. Let the Harness map them to ordered `RunEvent` values.
6. Do not persist tool state directly from the tool implementation.

To add another model profile:

1. Register an `AgentProfileDefinition` bean.
2. Register the corresponding `AgentProfileRuntime` bean.
3. Send its string ID as `profileId`; no Harness or prompt renderer branch is
   required.

## Deferred Work

- Resume endpoint based on checkpoints
- Artifact storage
- Human approval and resumable execution for destructive coding operations
- Per-Run workspace change journal and rollback
- Graph or multi-agent orchestration when deterministic workflow branching is
  required
- Request-scoped multi-tenant identity beyond the current deployment-scoped
  tenant/owner boundary
- OpenTelemetry traces and metrics
