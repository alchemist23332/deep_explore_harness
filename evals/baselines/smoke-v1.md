# Deep Explore Smoke Baseline V1

## Run

- Promptfoo eval ID: `eval-ne1-2026-08-16T06:55:41`
- Target: local Deep Explore `FAST` mode
- Target model: `deepseek-v4-flash`
- Judge model: `deepseek-v4-pro`
- Cases: 10 isolated single-turn conversations
- Concurrency: 1
- Cache: disabled
- Result: 9 passed, 1 failed, 0 errors
- Pass rate: 90%

## Timing

The custom SSE provider measures from request start until the `done` event.

| Metric | Average | P50 | P95 | Min | Max |
|---|---:|---:|---:|---:|---:|
| TTFT | 378 ms | 380 ms | 703 ms | 258 ms | 703 ms |
| End-to-end latency | 833 ms | 582 ms | 2026 ms | 438 ms | 2026 ms |

## Grading

- Judge token usage: 1,827 total
- Judge prompt tokens: 1,522
- Judge completion tokens: 305
- Target token usage: unavailable through the current legacy SSE contract

The judge uses `thinking.type=disabled` so rubric grading returns deterministic
JSON instead of reasoning-only responses.

## Failure

`07 concise summary` failed the deterministic length constraint:

- Required: at most 60 non-whitespace characters
- Actual: 70 non-whitespace characters
- Semantic summary quality: passed

This is a real instruction-following miss rather than a transport, parser, or
judge failure.

## Artifacts

The local, untracked reports are generated at:

- `evals/reports/smoke.html`
- `evals/reports/smoke.json`
