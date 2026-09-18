package com.alchemist.deepexplore.agent.adapter.langchain4j.tool.execution;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.service.tool.ToolExecutionResult;
import dev.langchain4j.service.tool.ToolExecutor;
import java.util.Objects;

public final class ScheduledToolExecutor implements ToolExecutor {

    private final ToolExecutor delegate;
    private final ToolBatchRegistry batches;

    public ScheduledToolExecutor(
            ToolExecutor delegate,
            ToolBatchRegistry batches
    ) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.batches = Objects.requireNonNull(batches, "batches");
    }

    @Override
    public String execute(ToolExecutionRequest request, Object memoryId) {
        return batches.execute(
                request,
                memoryId,
                () -> delegate.execute(request, memoryId)
        );
    }

    @Override
    public ToolExecutionResult executeWithContext(
            ToolExecutionRequest request,
            InvocationContext context
    ) {
        return batches.execute(
                request,
                context.chatMemoryId(),
                () -> delegate.executeWithContext(request, context)
        );
    }
}
