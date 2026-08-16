# Deep Explore Promptfoo Evals

This directory contains black-box evaluations for the Spring Boot backend. The
frontend is not involved. A small Promptfoo provider calls
`POST /api/chat/stream` in FAST mode, reconstructs the SSE response, records
stream timing, and evaluates ten isolated single-turn questions.

## Prerequisites

- Node.js 22.22 or newer
- macOS on Apple Silicon (the checked-in SQLite binding matches this workspace)
- PostgreSQL 17
- Deep Explore backend configuration in the repository root `.env`
- A dedicated `deep_explore_eval` database

Create the evaluation database once:

```bash
createdb -O deep_explore deep_explore_eval
```

Start the backend from the repository root with the evaluation database:

```bash
set -a
source .env
set +a
export DB_URL=jdbc:postgresql://localhost:5432/deep_explore_eval

cd deep_explore
./mvnw spring-boot:run
```

The backend should respond at `http://127.0.0.1:8080/api/health`.

## Install

```bash
cd evals
npm ci --omit=optional
```

## Validate And Run

Load the model credentials for the DeepSeek judge, validate the configuration,
and run the suite without cache or parallel requests:

```bash
set -a
source ../.env
set +a

npm run validate
npm run eval:smoke
```

The suite writes:

- `reports/smoke.html`: standalone review report
- `reports/smoke.json`: complete machine-readable result

Open the local Promptfoo viewer after a run:

```bash
npm run view
```

Set `DEEP_EXPLORE_BASE_URL` to target a different local backend address.

## Scope

The first suite measures final-answer correctness, instruction following,
uncertainty handling, TTFT, end-to-end latency, and provider errors. It does
not measure target token cost, multi-turn behavior, tools, or OpenTelemetry
traces.
