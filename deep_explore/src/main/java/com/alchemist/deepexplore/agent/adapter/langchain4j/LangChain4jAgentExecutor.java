package com.alchemist.deepexplore.agent.adapter.langchain4j;

import com.alchemist.deepexplore.agent.adapter.langchain4j.memory.LangChain4jMemoryManager;
import com.alchemist.deepexplore.agent.adapter.langchain4j.tool.WebSearchRoutingContext;
import com.alchemist.deepexplore.agent.adapter.langchain4j.tool.execution.ToolBatchRegistry;
import com.alchemist.deepexplore.agent.adapter.langchain4j.tool.execution.ToolSchedulingException;
import com.alchemist.deepexplore.agent.application.AgentInvocationContextRegistry;
import com.alchemist.deepexplore.agent.application.ToolDescriptorRegistry;
import com.alchemist.deepexplore.agent.domain.AgentExecutionEvent;
import com.alchemist.deepexplore.agent.domain.AgentExecutionRequest;
import com.alchemist.deepexplore.agent.domain.AgentPreparationRequest;
import com.alchemist.deepexplore.agent.domain.AgentStateSnapshot;
import com.alchemist.deepexplore.agent.domain.ToolDescriptor;
import com.alchemist.deepexplore.agent.spi.AgentExecutor;
import com.alchemist.deepexplore.config.AgentLoopProperties;
import com.alchemist.deepexplore.config.AiModelProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.response.StreamingHandle;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.TokenStream;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

@Component
public class LangChain4jAgentExecutor implements AgentExecutor {

    private static final Logger log =
            LoggerFactory.getLogger(LangChain4jAgentExecutor.class);
    private final String agentId;
    private final AgentProfileRuntimeRegistry profileRuntimes;
    private final AgentLoopProperties loopProperties;
    private final AiModelProperties modelProperties;
    private final LangChain4jMemoryManager memoryManager;
    private final WebSearchRoutingContext webSearchRoutingContext;
    private final AgentInvocationContextRegistry invocationContexts;
    private final ToolBatchRegistry toolBatches;
    private final ToolDescriptorRegistry toolDescriptors;
    private final ObjectMapper objectMapper;
    private final int maxEventResultCharacters;

    public LangChain4jAgentExecutor(
            @Value("${ai.agent.id:assistant}") String agentId,
            AgentProfileRuntimeRegistry profileRuntimes,
            AgentLoopProperties loopProperties,
            AiModelProperties modelProperties,
            LangChain4jMemoryManager memoryManager,
            WebSearchRoutingContext webSearchRoutingContext,
            AgentInvocationContextRegistry invocationContexts,
            ToolBatchRegistry toolBatches,
            ToolDescriptorRegistry toolDescriptors,
            ObjectMapper objectMapper,
            @Value("${tools.web-search.max-event-result-characters:16384}")
            int maxEventResultCharacters
    ) {
        this.agentId = agentId;
        this.profileRuntimes = profileRuntimes;
        this.loopProperties = loopProperties;
        this.modelProperties = modelProperties;
        this.memoryManager = memoryManager;
        this.webSearchRoutingContext = webSearchRoutingContext;
        this.invocationContexts = invocationContexts;
        this.toolBatches = toolBatches;
        this.toolDescriptors = toolDescriptors;
        this.objectMapper = objectMapper;
        this.maxEventResultCharacters = maxEventResultCharacters;
    }

    @Override
    public String agentId() {
        return agentId;
    }

    @Override
    public boolean isConfigured() {
        return modelProperties.isConfigured();
    }

    @Override
    public AgentStateSnapshot prepare(AgentPreparationRequest request) {
        return memoryManager.prepare(request);
    }

    @Override
    public Flux<AgentExecutionEvent> execute(AgentExecutionRequest request) {
        if (!isConfigured()) {
            return Flux.just(new AgentExecutionEvent.Failed(
                    "MODEL_NOT_CONFIGURED",
                    "服务端尚未配置 AI_API_KEY"
            ));
        }

        return Flux.create(sink -> {
            AtomicBoolean active = new AtomicBoolean(true);
            AtomicReference<StreamingHandle> handle = new AtomicReference<>();
            sink.onCancel(() -> {
                if (active.compareAndSet(true, false)) {
                    toolBatches.cancelRun(request.runId());
                    clearRequestContexts(request);
                    StreamingHandle streamingHandle = handle.get();
                    if (streamingHandle != null) {
                        streamingHandle.cancel();
                    }
                }
            });

            try {
                webSearchRoutingContext.bind(
                        request.runId(),
                        request.searchProvider()
                );
                invocationContexts.bind(
                        request.runId(),
                        request.conversationId(),
                        request.workspaceId()
                );
                TokenStream stream = assistantFor(request.profileId())
                        .chat(request.runId(), request.message());
                stream.onPartialResponseWithContext((partial, context) -> {
                            registerHandle(
                                    active,
                                    handle,
                                    context.streamingHandle()
                            );
                            if (active.get()) {
                                sink.next(new AgentExecutionEvent.TextDelta(
                                        partial.text()
                                ));
                            }
                        })
                        .onPartialToolCallWithContext((partial, context) ->
                                registerHandle(
                                        active,
                                        handle,
                                        context.streamingHandle()
                                ))
                        .beforeToolExecution(tool -> {
                            if (active.get()) {
                                sink.next(new AgentExecutionEvent.ToolCallStarted(
                                        tool.request().id(),
                                        tool.request().name(),
                                        eventArguments(
                                                tool.request().name(),
                                                tool.request().arguments()
                                        )
                                ));
                            }
                        })
                        .onToolExecuted(tool -> {
                            if (active.get()) {
                                sink.next(new AgentExecutionEvent.ToolCallCompleted(
                                        tool.request().id(),
                                        tool.request().name(),
                                        eventResult(
                                                tool.request().name(),
                                                tool.result()
                                        ),
                                        !tool.hasFailed()
                                                && toolResultSucceeded(
                                                        tool.result()
                                                )
                                ));
                            }
                        })
                        .onCompleteResponse(response -> {
                            if (!active.compareAndSet(true, false)) {
                                return;
                            }
                            toolBatches.cancelRun(request.runId());
                            clearRequestContexts(request);
                            sink.next(new AgentExecutionEvent.Completed(
                                    response.aiMessage().text(),
                                    modelName(request.profileId()),
                                    totalTokens(response.tokenUsage())
                            ));
                            sink.complete();
                        })
                        .onError(error -> {
                            if (!active.compareAndSet(true, false)) {
                                return;
                            }
                            clearRequestContexts(request);
                            emitFailure(
                                    sink,
                                    request,
                                    error,
                                    "MODEL_CALL_FAILED",
                                    "模型调用失败，请检查模型地址、名称和 API Key"
                            );
                            sink.complete();
                        })
                        .start();
            } catch (RuntimeException error) {
                if (active.compareAndSet(true, false)) {
                    toolBatches.cancelRun(request.runId());
                    clearRequestContexts(request);
                    emitFailure(
                            sink,
                            request,
                            error,
                            "MODEL_START_FAILED",
                            "模型调用启动失败"
                    );
                    sink.complete();
                }
            }
        });
    }

    @Override
    public void restore(String conversationId, AgentStateSnapshot snapshot) {
        memoryManager.restore(conversationId, snapshot);
    }

    @Override
    public void invalidate(String conversationId) {
        memoryManager.invalidate(conversationId);
    }

    @Override
    public void markMemorySynchronized(
            String conversationId,
            String sourceHeadMessageId
    ) {
        memoryManager.markSynchronized(
                conversationId,
                sourceHeadMessageId
        );
    }

    @Override
    public void release(String conversationId, String runId) {
        webSearchRoutingContext.clear(runId);
        invocationContexts.clear(runId);
        profileRuntimes.list().forEach(runtime ->
                runtime.assistant().evictChatMemory(runId));
    }

    private StreamingAssistant assistantFor(String profileId) {
        return profileRuntimes.require(profileId).assistant();
    }

    private String modelName(String profileId) {
        return profileRuntimes.require(profileId).modelName();
    }

    private static Integer totalTokens(TokenUsage tokenUsage) {
        return tokenUsage == null ? null : tokenUsage.totalTokenCount();
    }

    private String truncate(String value) {
        if (value == null || value.length() <= maxEventResultCharacters) {
            return value;
        }
        return value.substring(0, maxEventResultCharacters)
                + "\n...[truncated for run event]";
    }

    private String eventResult(String toolName, String result) {
        ToolDescriptor descriptor = toolDescriptors.descriptor(toolName);
        if (descriptor.resultExposure()
                == ToolDescriptor.ResultExposure.NONE) {
            return null;
        }
        if (result == null || result.isBlank()) {
            return result;
        }
        try {
            JsonNode parsed = objectMapper.readTree(result);
            JsonNode summary = parsed.get("summary");
            if (summary != null && summary.isTextual()) {
                JsonNode code = parsed.get("code");
                return objectMapper.writeValueAsString(
                        code == null || code.isNull()
                                ? java.util.Map.of(
                                        "ok",
                                        parsed.path("ok").asBoolean(),
                                        "summary",
                                        summary.asText()
                                )
                                : java.util.Map.of(
                                        "ok",
                                        parsed.path("ok").asBoolean(),
                                        "summary",
                                        summary.asText(),
                                        "code",
                                        code.asText()
                                )
                );
            }
        } catch (Exception ignored) {
            // Non-structured tool results use the normal event truncation.
        }
        return truncate(result);
    }

    private String eventArguments(String toolName, String arguments) {
        ToolDescriptor descriptor = toolDescriptors.descriptor(toolName);
        String field = switch (descriptor.argumentExposure()) {
            case DESCRIPTION -> "description";
            case QUERY -> "query";
            case NONE -> null;
        };
        if (field == null) {
            return "{}";
        }
        try {
            JsonNode parsed = objectMapper.readTree(arguments);
            JsonNode value = parsed.get(field);
            if (value != null && value.isTextual()) {
                return objectMapper.writeValueAsString(java.util.Map.of(
                        field,
                        value.asText()
                ));
            }
        } catch (Exception ignored) {
            // Invalid arguments are reported by the tool execution handler.
        }
        return "{}";
    }

    private boolean toolResultSucceeded(String result) {
        if (result == null || result.isBlank()) {
            return true;
        }
        try {
            JsonNode parsed = objectMapper.readTree(result);
            return !parsed.has("ok") || parsed.path("ok").asBoolean();
        } catch (Exception ignored) {
            return true;
        }
    }

    private void clearRequestContexts(AgentExecutionRequest request) {
        webSearchRoutingContext.clear(request.runId());
        invocationContexts.clear(request.runId());
    }

    private static void registerHandle(
            AtomicBoolean active,
            AtomicReference<StreamingHandle> reference,
            StreamingHandle handle
    ) {
        if (handle == null) {
            return;
        }
        reference.compareAndSet(null, handle);
        if (!active.get()) {
            handle.cancel();
        }
    }

    private static boolean isToolRoundLimitExceeded(Throwable error) {
        Throwable current = error;
        while (current != null) {
            String message = current.getMessage();
            if (message != null
                    && message.contains("maxToolCallingRoundTrips")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static ToolSchedulingException findToolSchedulingError(
            Throwable error
    ) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof ToolSchedulingException schedulingError) {
                return schedulingError;
            }
            current = current.getCause();
        }
        return null;
    }

    private void emitFailure(
            FluxSink<AgentExecutionEvent> sink,
            AgentExecutionRequest request,
            Throwable error,
            String fallbackCode,
            String fallbackMessage
    ) {
        boolean toolRoundLimitExceeded = isToolRoundLimitExceeded(error);
        ToolSchedulingException schedulingError =
                findToolSchedulingError(error);
        if (toolRoundLimitExceeded) {
            log.warn(
                    "Tool round limit reached for run {} with profile {} "
                            + "(limit {})",
                    request.runId(),
                    request.profileId(),
                    loopProperties.maxToolCallingRoundTrips(
                            request.profileId()
                    )
            );
        } else if (schedulingError != null) {
            log.warn(
                    "Tool scheduling failed for run {}: {} ({})",
                    request.runId(),
                    schedulingError.getMessage(),
                    schedulingError.code()
            );
        } else {
            log.error(
                    "Model execution failed for run {}",
                    request.runId(),
                    error
            );
        }
        sink.next(new AgentExecutionEvent.Failed(
                toolRoundLimitExceeded
                        ? "TOOL_ROUND_LIMIT_EXCEEDED"
                        : schedulingError != null
                                ? schedulingError.code()
                                : fallbackCode,
                toolRoundLimitExceeded
                        ? "Agent 已达到本次任务的工具调用轮次上限。"
                                + "已完成的读取和修改均已保留，"
                                + "可以发送“继续”接着执行。"
                        : schedulingError != null
                                ? "工具批次调度失败："
                                        + schedulingError.getMessage()
                                : fallbackMessage
        ));
    }
}
