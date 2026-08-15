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
  application/              Executor registry and compatibility facade
  spi/                      AgentExecutor extension point
  adapter/langchain4j/      LangChain4j implementation and ChatMemory

harness/
  domain/                   Run, event, status, and checkpoint types
  application/              Run lifecycle orchestration
  port/                     Run, event, and checkpoint persistence interfaces
  adapter/out/postgres/     PostgreSQL implementations
```

The dependency direction is:

```text
Web -> Application -> Domain/Port <- Infrastructure Adapter
Harness -> Agent SPI
Harness -> Conversation Ports
Conversation -X-> Agent/Harness
Domain -X-> Spring/JDBC/LangChain4j/Reactor
```

ArchUnit tests enforce these rules.

## Runtime Flow

1. The legacy HTTP endpoint creates an `AgentCommand`.
2. `AgentServiceFacade` maps FAST/DEEP to an execution profile and delegates to
   `HarnessService`.
3. `HarnessOrchestrator` creates an `AgentRun` and emits `RunStarted`.
4. The Harness acquires the conversation lock and snapshots Agent state.
5. The snapshot is persisted as a checkpoint.
6. The user message is appended to complete conversation history.
7. `AgentExecutorRegistry` selects an executor by `agentId`.
8. `LangChain4jAgentExecutor` streams framework-neutral execution events.
9. The Harness assigns ordered event sequence numbers and persists each event.
10. Completion writes the assistant message and closes the Run.
11. Failure or cancellation restores the checkpoint and releases the lock.

## Compatibility

`POST /api/chat/stream` and the existing SSE event names remain unchanged.
`AgentService` is retained as a compatibility facade. New code should depend on
`HarnessService`, `AgentExecutor`, and typed `RunEvent` contracts.

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

## Extension Rules

To add an Agent:

1. Implement `AgentExecutor`.
2. Return a unique `agentId`.
3. Register it as a Spring bean.
4. Send that `agentId` in `StartRunCommand`.

To add a tool or approval flow:

1. Emit the corresponding typed `AgentExecutionEvent`.
2. Let the Harness map it to an ordered `RunEvent`.
3. Do not persist tool state directly from the Agent adapter.

To add another model profile:

1. Add a profile configuration.
2. Resolve it inside the Agent adapter.
3. Do not add another branch to the Harness.

## Deferred Work

- User and tenant ownership
- Resume endpoint based on checkpoints
- Tool registry and approval API
- Artifact storage
- Batched persistence for high-volume text delta events
- OpenTelemetry traces and metrics
