package com.alchemist.deepexplore.agent.adapter.langchain4j.tool.execution;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/**
 * Holds short-lived execution plans for one model response. Plans are keyed by
 * tool-call id because LangChain4j invokes each ToolExecutor independently.
 */
@Component
public class ToolBatchRegistry {

    private static final Duration CANCEL_MARKER_TTL = Duration.ofMinutes(5);
    private static final Duration PERMIT_POLL_INTERVAL =
            Duration.ofMillis(100);

    private final ToolPolicyRegistry policies;
    private final ToolExecutionProperties properties;
    private final Semaphore parallelism;
    private final ConcurrentMap<String, PlannedCall> calls =
            new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, BatchState> batches =
            new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Set<BatchState>> batchesByRun =
            new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Long> cancelledRuns =
            new ConcurrentHashMap<>();

    public ToolBatchRegistry(
            ToolPolicyRegistry policies,
            ToolExecutionProperties properties
    ) {
        this.policies = policies;
        this.properties = properties;
        this.parallelism = new Semaphore(
                properties.maxParallelism(),
                true
        );
    }

    public BatchHandle register(List<ToolExecutionRequest> requests) {
        if (requests.size() > properties.maxCallsPerRound()) {
            throw new ToolSchedulingException(
                    "TOOL_BATCH_TOO_LARGE",
                    "Model requested " + requests.size()
                            + " tools in one round; maximum is "
                            + properties.maxCallsPerRound()
            );
        }
        if (requests.isEmpty()) {
            throw new IllegalArgumentException("Tool batch must not be empty");
        }

        List<PlanEntry> entries = new ArrayList<>(requests.size());
        int stageIndex = -1;
        boolean readStage = false;
        for (int index = 0; index < requests.size(); index++) {
            ToolExecutionRequest request = requests.get(index);
            requireToolCallId(request);
            ToolExecutionPolicy policy = policies.policyFor(request.name());
            if (policy.canRunConcurrently()) {
                if (!readStage) {
                    stageIndex++;
                    readStage = true;
                }
            } else {
                stageIndex++;
                readStage = false;
            }
            entries.add(new PlanEntry(request, index, stageIndex, policy));
        }

        int[] stageSizes = new int[stageIndex + 1];
        entries.forEach(entry -> stageSizes[entry.stageIndex()]++);
        BatchState batch = new BatchState(
                UUID.randomUUID(),
                entries.size(),
                stageSizes
        );
        List<PlannedCall> registered = new ArrayList<>(entries.size());
        try {
            for (PlanEntry entry : entries) {
                PlannedCall planned = new PlannedCall(
                        batch,
                        entry.request().id(),
                        entry.request().name(),
                        entry.index(),
                        entry.stageIndex(),
                        entry.policy()
                );
                PlannedCall previous = calls.putIfAbsent(
                        planned.toolCallId(),
                        planned
                );
                if (previous != null) {
                    throw new ToolSchedulingException(
                            "DUPLICATE_TOOL_CALL_ID",
                            "Tool call id is already active: "
                                    + planned.toolCallId()
                    );
                }
                registered.add(planned);
                batch.calls.add(planned);
            }
            batches.put(batch.id, batch);
            return new BatchHandle(batch.id);
        } catch (RuntimeException error) {
            registered.forEach(planned ->
                    calls.remove(planned.toolCallId(), planned));
            throw error;
        }
    }

    public <T> T execute(
            ToolExecutionRequest request,
            Object runId,
            Supplier<T> action
    ) {
        PlannedCall planned = calls.get(request.id());
        if (planned == null || !planned.toolName().equals(request.name())) {
            throw new ToolSchedulingException(
                    "TOOL_BATCH_PLAN_MISSING",
                    "No matching execution plan for tool call " + request.id()
            );
        }

        String normalizedRunId = runId == null ? "" : runId.toString();
        boolean permit = false;
        try {
            bindRun(planned.batch(), normalizedRunId);
            if (isRunCancelled(normalizedRunId)) {
                planned.batch().cancel();
            }
            planned.batch().stages.get(planned.stageIndex()).await(
                    properties.barrierTimeout()
            );
            requireActive(planned.batch());
            permit = acquirePermit(planned.batch());
            if (!permit) {
                throw new ToolSchedulingException(
                        "TOOL_EXECUTION_TIMEOUT",
                        "Timed out waiting for a tool execution slot"
                );
            }
            requireActive(planned.batch());
            return action.get();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new ToolSchedulingException(
                    "TOOL_EXECUTION_INTERRUPTED",
                    "Tool execution was interrupted",
                    error
            );
        } finally {
            if (permit) {
                parallelism.release();
            }
            complete(planned);
        }
    }

    public void cancelRun(String runId) {
        if (runId == null || runId.isBlank()) {
            return;
        }
        cancelledRuns.put(
                runId,
                System.nanoTime() + CANCEL_MARKER_TTL.toNanos()
        );
        Set<BatchState> runBatches = batchesByRun.get(runId);
        if (runBatches != null) {
            runBatches.forEach(BatchState::cancel);
        }
    }

    public void cancelBatch(BatchHandle handle) {
        if (handle == null) {
            return;
        }
        BatchState batch = batches.get(handle.id());
        if (batch != null) {
            batch.cancel();
            cleanup(batch);
        }
    }

    public boolean barrierEnabled() {
        return properties.barrierEnabled();
    }

    private boolean acquirePermit(BatchState batch)
            throws InterruptedException {
        long timeoutNanos = properties.barrierTimeout().toNanos();
        long deadline = System.nanoTime() + timeoutNanos;
        while (true) {
            requireActive(batch);
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) {
                return false;
            }
            long waitNanos = Math.min(
                    remaining,
                    PERMIT_POLL_INTERVAL.toNanos()
            );
            if (parallelism.tryAcquire(
                    waitNanos,
                    TimeUnit.NANOSECONDS
            )) {
                return true;
            }
        }
    }

    private void bindRun(BatchState batch, String runId) {
        if (runId.isBlank()) {
            return;
        }
        synchronized (batch) {
            if (batch.cleaned.get()) {
                throw new ToolSchedulingException(
                        "TOOL_EXECUTION_CANCELLED",
                        "Tool execution batch is no longer active"
                );
            }
            if (batch.runId == null) {
                batch.runId = runId;
                batchesByRun.computeIfAbsent(
                        runId,
                        ignored -> ConcurrentHashMap.newKeySet()
                ).add(batch);
            } else if (!batch.runId.equals(runId)) {
                throw new ToolSchedulingException(
                        "TOOL_BATCH_RUN_MISMATCH",
                        "Tool batch was invoked by multiple Agent runs"
                );
            }
        }
    }

    private boolean isRunCancelled(String runId) {
        if (runId.isBlank()) {
            return false;
        }
        Long deadline = cancelledRuns.get(runId);
        if (deadline == null) {
            return false;
        }
        if (System.nanoTime() <= deadline) {
            return true;
        }
        cancelledRuns.remove(runId, deadline);
        return false;
    }

    private void complete(PlannedCall planned) {
        if (!planned.completed().compareAndSet(false, true)) {
            return;
        }
        planned.batch().stages.get(planned.stageIndex()).completeOne();
        if (planned.batch().remaining.decrementAndGet() == 0) {
            cleanup(planned.batch());
        }
    }

    private void cleanup(BatchState batch) {
        if (!batch.cleaned.compareAndSet(false, true)) {
            return;
        }
        synchronized (batch) {
            batches.remove(batch.id, batch);
            batch.calls.forEach(planned ->
                    calls.remove(planned.toolCallId(), planned));
            if (batch.runId != null) {
                batchesByRun.computeIfPresent(
                        batch.runId,
                        (ignored, values) -> {
                            values.remove(batch);
                            return values.isEmpty() ? null : values;
                        }
                );
                cancelledRuns.remove(batch.runId);
            }
        }
    }

    private static void requireActive(BatchState batch) {
        if (batch.cancelled.get()) {
            throw new ToolSchedulingException(
                    "TOOL_EXECUTION_CANCELLED",
                    "Tool execution was cancelled"
            );
        }
    }

    private static void requireToolCallId(ToolExecutionRequest request) {
        if (request.id() == null || request.id().isBlank()) {
            throw new ToolSchedulingException(
                    "TOOL_CALL_ID_REQUIRED",
                    "Parallel tool execution requires a tool call id"
            );
        }
    }

    public record BatchHandle(UUID id) {
    }

    private record PlanEntry(
            ToolExecutionRequest request,
            int index,
            int stageIndex,
            ToolExecutionPolicy policy
    ) {
    }

    private record PlannedCall(
            BatchState batch,
            String toolCallId,
            String toolName,
            int index,
            int stageIndex,
            ToolExecutionPolicy policy,
            AtomicBoolean completed
    ) {

        private PlannedCall(
                BatchState batch,
                String toolCallId,
                String toolName,
                int index,
                int stageIndex,
                ToolExecutionPolicy policy
        ) {
            this(
                    batch,
                    toolCallId,
                    toolName,
                    index,
                    stageIndex,
                    policy,
                    new AtomicBoolean()
            );
        }
    }

    private static final class BatchState {

        private final UUID id;
        private final AtomicInteger remaining;
        private final List<StageState> stages;
        private final List<PlannedCall> calls = new ArrayList<>();
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private final AtomicBoolean cleaned = new AtomicBoolean();
        private volatile String runId;

        private BatchState(UUID id, int callCount, int[] stageSizes) {
            this.id = id;
            this.remaining = new AtomicInteger(callCount);
            this.stages = new ArrayList<>(stageSizes.length);
            CompletableFuture<Void> previous =
                    CompletableFuture.completedFuture(null);
            for (int stageSize : stageSizes) {
                StageState stage = new StageState(previous, stageSize);
                stages.add(stage);
                previous = stage.done;
            }
        }

        private void cancel() {
            if (cancelled.compareAndSet(false, true)) {
                ToolSchedulingException error = new ToolSchedulingException(
                        "TOOL_EXECUTION_CANCELLED",
                        "Tool execution was cancelled"
                );
                stages.forEach(stage -> stage.done.completeExceptionally(error));
            }
        }
    }

    private static final class StageState {

        private final CompletableFuture<Void> ready;
        private final CompletableFuture<Void> done = new CompletableFuture<>();
        private final AtomicInteger remaining;

        private StageState(
                CompletableFuture<Void> ready,
                int callCount
        ) {
            this.ready = ready;
            this.remaining = new AtomicInteger(callCount);
        }

        private void await(Duration timeout) throws InterruptedException {
            try {
                ready.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            } catch (ExecutionException error) {
                Throwable cause = error.getCause();
                if (cause instanceof ToolSchedulingException schedulingError) {
                    throw schedulingError;
                }
                throw new ToolSchedulingException(
                        "TOOL_BARRIER_FAILED",
                        "Previous tool execution stage failed",
                        cause
                );
            } catch (TimeoutException error) {
                throw new ToolSchedulingException(
                        "TOOL_BARRIER_TIMEOUT",
                        "Timed out waiting for the previous tool stage",
                        error
                );
            }
        }

        private void completeOne() {
            if (remaining.decrementAndGet() == 0) {
                done.complete(null);
            }
        }
    }
}
