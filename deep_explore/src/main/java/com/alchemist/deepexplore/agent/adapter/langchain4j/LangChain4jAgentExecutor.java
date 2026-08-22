package com.alchemist.deepexplore.agent.adapter.langchain4j;

import com.alchemist.deepexplore.agent.adapter.langchain4j.memory.LangChain4jMemoryManager;
import com.alchemist.deepexplore.agent.adapter.langchain4j.tool.WebSearchRoutingContext;
import com.alchemist.deepexplore.agent.domain.AgentExecutionEvent;
import com.alchemist.deepexplore.agent.domain.AgentExecutionRequest;
import com.alchemist.deepexplore.agent.domain.AgentPreparationRequest;
import com.alchemist.deepexplore.agent.domain.AgentProfile;
import com.alchemist.deepexplore.agent.domain.AgentStateSnapshot;
import com.alchemist.deepexplore.agent.spi.AgentExecutor;
import com.alchemist.deepexplore.config.AiModelProperties;
import dev.langchain4j.model.chat.response.StreamingHandle;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.TokenStream;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Component
public class LangChain4jAgentExecutor implements AgentExecutor {

    private static final Logger log =
            LoggerFactory.getLogger(LangChain4jAgentExecutor.class);

    private final String agentId;
    private final StreamingAssistant fastAssistant;
    private final StreamingAssistant deepAssistant;
    private final AiModelProperties modelProperties;
    private final LangChain4jMemoryManager memoryManager;
    private final WebSearchRoutingContext webSearchRoutingContext;
    private final int maxEventResultCharacters;

    public LangChain4jAgentExecutor(
            @Value("${ai.agent.id:assistant}") String agentId,
            @Qualifier("fastStreamingAssistant") StreamingAssistant fastAssistant,
            @Qualifier("deepStreamingAssistant") StreamingAssistant deepAssistant,
            AiModelProperties modelProperties,
            LangChain4jMemoryManager memoryManager,
            WebSearchRoutingContext webSearchRoutingContext,
            @Value("${tools.web-search.max-event-result-characters:16384}")
            int maxEventResultCharacters
    ) {
        this.agentId = agentId;
        this.fastAssistant = fastAssistant;
        this.deepAssistant = deepAssistant;
        this.modelProperties = modelProperties;
        this.memoryManager = memoryManager;
        this.webSearchRoutingContext = webSearchRoutingContext;
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
                    webSearchRoutingContext.clear(request.conversationId());
                    StreamingHandle streamingHandle = handle.get();
                    if (streamingHandle != null) {
                        streamingHandle.cancel();
                    }
                }
            });

            try {
                webSearchRoutingContext.bind(
                        request.conversationId(),
                        request.searchProvider()
                );
                TokenStream stream = assistantFor(request.profileId())
                        .chat(request.conversationId(), request.message());
                stream.onPartialResponseWithContext((partial, context) -> {
                            handle.compareAndSet(null, context.streamingHandle());
                            if (active.get()) {
                                sink.next(new AgentExecutionEvent.TextDelta(
                                        partial.text()
                                ));
                            }
                        })
                        .onPartialToolCallWithContext((partial, context) ->
                                handle.compareAndSet(null, context.streamingHandle()))
                        .beforeToolExecution(tool -> {
                            if (active.get()) {
                                sink.next(new AgentExecutionEvent.ToolCallStarted(
                                        tool.request().id(),
                                        tool.request().name(),
                                        truncate(tool.request().arguments())
                                ));
                            }
                        })
                        .onToolExecuted(tool -> {
                            if (active.get()) {
                                sink.next(new AgentExecutionEvent.ToolCallCompleted(
                                        tool.request().id(),
                                        tool.request().name(),
                                        truncate(tool.result()),
                                        !tool.hasFailed()
                                ));
                            }
                        })
                        .onCompleteResponse(response -> {
                            if (!active.compareAndSet(true, false)) {
                                return;
                            }
                            webSearchRoutingContext.clear(request.conversationId());
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
                            webSearchRoutingContext.clear(request.conversationId());
                            log.error(
                                    "Model call failed for run {}",
                                    request.runId(),
                                    error
                            );
                            sink.next(new AgentExecutionEvent.Failed(
                                    "MODEL_CALL_FAILED",
                                    "模型调用失败，请检查模型地址、名称和 API Key"
                            ));
                            sink.complete();
                        })
                        .start();
            } catch (RuntimeException error) {
                if (active.compareAndSet(true, false)) {
                    webSearchRoutingContext.clear(request.conversationId());
                    log.error("Unable to start model run {}", request.runId(), error);
                    sink.next(new AgentExecutionEvent.Failed(
                            "MODEL_START_FAILED",
                            "模型调用启动失败"
                    ));
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
    public void release(String conversationId) {
        webSearchRoutingContext.clear(conversationId);
        fastAssistant.evictChatMemory(conversationId);
        deepAssistant.evictChatMemory(conversationId);
    }

    private StreamingAssistant assistantFor(String profileId) {
        return AgentProfile.DEEP.id().equalsIgnoreCase(profileId)
                ? deepAssistant
                : fastAssistant;
    }

    private String modelName(String profileId) {
        return AgentProfile.DEEP.id().equalsIgnoreCase(profileId)
                ? modelProperties.resolvedDeepModelName()
                : modelProperties.modelName();
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
}
