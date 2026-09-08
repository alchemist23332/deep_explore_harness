package com.alchemist.deepexplore.agent.adapter.langchain4j;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.alchemist.deepexplore.agent.adapter.langchain4j.memory.LangChain4jMemoryManager;
import com.alchemist.deepexplore.agent.adapter.langchain4j.tool.WebSearchRoutingContext;
import com.alchemist.deepexplore.agent.application.AgentInvocationContextRegistry;
import com.alchemist.deepexplore.agent.application.ToolDescriptorRegistry;
import com.alchemist.deepexplore.agent.domain.AgentExecutionEvent;
import com.alchemist.deepexplore.agent.domain.AgentExecutionRequest;
import com.alchemist.deepexplore.agent.domain.AgentProfile;
import com.alchemist.deepexplore.agent.domain.WebSearchProvider;
import com.alchemist.deepexplore.agent.domain.ToolDescriptor;
import com.alchemist.deepexplore.config.AiModelProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.PartialResponse;
import dev.langchain4j.model.chat.response.PartialResponseContext;
import dev.langchain4j.model.chat.response.PartialToolCall;
import dev.langchain4j.model.chat.response.PartialToolCallContext;
import dev.langchain4j.model.chat.response.StreamingHandle;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.tool.BeforeToolExecution;
import dev.langchain4j.service.tool.ToolExecution;
import dev.langchain4j.service.tool.ToolExecutionResult;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import reactor.core.Disposable;
import reactor.test.StepVerifier;

class LangChain4jAgentExecutorTest {

    @Test
    void emitsToolLifecycleAndFinalResponse() {
        StreamingAssistant fastAssistant = mock(StreamingAssistant.class);
        StreamingAssistant deepAssistant = mock(StreamingAssistant.class);
        when(fastAssistant.chat("run-1", "latest news"))
                .thenReturn(new ToolCallingTokenStream());
        WebSearchRoutingContext routingContext =
                mock(WebSearchRoutingContext.class);
        AgentInvocationContextRegistry invocationContexts =
                mock(AgentInvocationContextRegistry.class);

        LangChain4jAgentExecutor executor = new LangChain4jAgentExecutor(
                "assistant",
                runtimes(fastAssistant, deepAssistant),
                properties(),
                mock(LangChain4jMemoryManager.class),
                routingContext,
                invocationContexts,
                toolDescriptors(),
                new ObjectMapper(),
                12
        );

        StepVerifier.create(executor.execute(new AgentExecutionRequest(
                        "run-1",
                        "conversation-1",
                        "assistant",
                        AgentProfile.FAST.id(),
                        "latest news",
                        WebSearchProvider.TAVILY,
                        "workspace-1"
                )))
                .assertNext(event -> {
                    var started = (AgentExecutionEvent.ToolCallStarted) event;
                    assertThat(started.toolCallId()).isEqualTo("tool-1");
                    assertThat(started.toolName()).isEqualTo("web_search");
                })
                .assertNext(event -> {
                    var completed = (AgentExecutionEvent.ToolCallCompleted) event;
                    assertThat(completed.success()).isTrue();
                    assertThat(completed.result()).isNull();
                })
                .assertNext(event -> {
                    var completed = (AgentExecutionEvent.Completed) event;
                    assertThat(completed.text()).isEqualTo("final answer");
                    assertThat(completed.model()).isEqualTo("deepseek-v4-flash");
                    assertThat(completed.tokenUsage()).isEqualTo(3);
                })
                .verifyComplete();

        verify(routingContext).bind(
                "run-1",
                WebSearchProvider.TAVILY
        );
        verify(routingContext).clear("run-1");
        verify(invocationContexts).bind(
                "run-1",
                "conversation-1",
                "workspace-1"
        );
        verify(invocationContexts).clear("run-1");
    }

    @Test
    void cancelsHandlePublishedAfterSubscriberCancellation() {
        StreamingAssistant fastAssistant = mock(StreamingAssistant.class);
        DelayedTokenStream stream = new DelayedTokenStream();
        when(fastAssistant.chat("run-1", "wait"))
                .thenReturn(stream);
        LangChain4jAgentExecutor executor = new LangChain4jAgentExecutor(
                "assistant",
                runtimes(fastAssistant, mock(StreamingAssistant.class)),
                properties(),
                mock(LangChain4jMemoryManager.class),
                mock(WebSearchRoutingContext.class),
                mock(AgentInvocationContextRegistry.class),
                toolDescriptors(),
                new ObjectMapper(),
                1_000
        );
        StreamingHandle handle = mock(StreamingHandle.class);
        PartialResponseContext context = mock(PartialResponseContext.class);
        when(context.streamingHandle()).thenReturn(handle);

        Disposable subscription = executor.execute(new AgentExecutionRequest(
                "run-1",
                "conversation-1",
                "assistant",
                AgentProfile.FAST.id(),
                "wait"
        )).subscribe();
        subscription.dispose();
        stream.publishHandle(context);

        verify(handle).cancel();
    }

    @Test
    void removesCodingPayloadsFromRunEvents() {
        StreamingAssistant fastAssistant = mock(StreamingAssistant.class);
        when(fastAssistant.chat("run-1", "change file"))
                .thenReturn(new ToolCallingTokenStream(
                        "write_file",
                        "{\"description\":\"Update app\","
                                + "\"path\":\"App.java\","
                                + "\"content\":\"secret source\"}",
                        "{\"ok\":true,\"summary\":\"Wrote App.java\","
                                + "\"data\":{\"content\":\"secret source\"}}"
                ));
        AgentInvocationContextRegistry invocationContexts =
                mock(AgentInvocationContextRegistry.class);
        LangChain4jAgentExecutor executor = new LangChain4jAgentExecutor(
                "assistant",
                runtimes(fastAssistant, mock(StreamingAssistant.class)),
                properties(),
                mock(LangChain4jMemoryManager.class),
                mock(WebSearchRoutingContext.class),
                invocationContexts,
                toolDescriptors(),
                new ObjectMapper(),
                1_000
        );

        StepVerifier.create(executor.execute(new AgentExecutionRequest(
                        "run-1",
                        "conversation-1",
                        "assistant",
                        AgentProfile.FAST.id(),
                        "change file",
                        WebSearchProvider.TAVILY,
                        "workspace-1"
                )))
                .assertNext(event -> {
                    var started = (AgentExecutionEvent.ToolCallStarted) event;
                    assertThat(started.argumentsJson())
                            .isEqualTo("{\"description\":\"Update app\"}");
                })
                .assertNext(event -> {
                    var completed =
                            (AgentExecutionEvent.ToolCallCompleted) event;
                    assertThat(completed.result())
                            .contains("\"summary\":\"Wrote App.java\"")
                            .contains("\"ok\":true")
                            .doesNotContain("secret source");
                })
                .expectNextCount(1)
                .verifyComplete();
    }

    @Test
    void reportsToolRoundLimitSeparatelyFromModelFailures() {
        StreamingAssistant fastAssistant = mock(StreamingAssistant.class);
        when(fastAssistant.chat("run-1", "large coding task"))
                .thenReturn(new ToolCallingTokenStream(
                        new RuntimeException(
                                "Something is wrong, exceeded 16 tool calling "
                                        + "round trips "
                                        + "(maxToolCallingRoundTrips)"
                        )
                ));
        AgentInvocationContextRegistry invocationContexts =
                mock(AgentInvocationContextRegistry.class);
        LangChain4jAgentExecutor executor = new LangChain4jAgentExecutor(
                "assistant",
                runtimes(fastAssistant, mock(StreamingAssistant.class)),
                properties(),
                mock(LangChain4jMemoryManager.class),
                mock(WebSearchRoutingContext.class),
                invocationContexts,
                toolDescriptors(),
                new ObjectMapper(),
                1_000
        );

        StepVerifier.create(executor.execute(new AgentExecutionRequest(
                        "run-1",
                        "conversation-1",
                        "assistant",
                        AgentProfile.FAST.id(),
                        "large coding task",
                        WebSearchProvider.TAVILY,
                        "workspace-1"
                )))
                .assertNext(event -> {
                    var failed = (AgentExecutionEvent.Failed) event;
                    assertThat(failed.code())
                            .isEqualTo("TOOL_ROUND_LIMIT_EXCEEDED");
                    assertThat(failed.message()).contains("工具调用轮次");
                })
                .verifyComplete();
        verify(invocationContexts).clear("run-1");
    }

    private static AiModelProperties properties() {
        return new AiModelProperties(
                "DEEPSEEK",
                "test-key",
                "https://example.test",
                "deepseek-v4-flash",
                "deepseek-v4-pro",
                0.3,
                1024,
                "none",
                4096,
                "high",
                "disabled",
                "enabled",
                Duration.ofSeconds(60),
                false,
                false
        );
    }

    private static AgentProfileRuntimeRegistry runtimes(
            StreamingAssistant fast,
            StreamingAssistant deep
    ) {
        return new AgentProfileRuntimeRegistry(List.of(
                new AgentProfileRuntime(
                        AgentProfile.FAST.id(),
                        "deepseek-v4-flash",
                        fast
                ),
                new AgentProfileRuntime(
                        AgentProfile.DEEP.id(),
                        "deepseek-v4-pro",
                        deep
                )
        ));
    }

    private static ToolDescriptorRegistry toolDescriptors() {
        return new ToolDescriptorRegistry(List.of(() -> List.of(
                new ToolDescriptor(
                        "web_search",
                        "网页搜索",
                        ToolDescriptor.ArgumentExposure.QUERY,
                        ToolDescriptor.ResultExposure.NONE
                ),
                new ToolDescriptor(
                        "write_file",
                        "写入文件",
                        ToolDescriptor.ArgumentExposure.DESCRIPTION,
                        ToolDescriptor.ResultExposure.SUMMARY
                )
        )));
    }

    private static final class DelayedTokenStream implements TokenStream {

        private BiConsumer<PartialResponse, PartialResponseContext>
                partialResponseHandler;

        void publishHandle(PartialResponseContext context) {
            partialResponseHandler.accept(null, context);
        }

        @Override
        public TokenStream onPartialResponse(Consumer<String> handler) {
            return this;
        }

        @Override
        public TokenStream onPartialResponseWithContext(
                BiConsumer<PartialResponse, PartialResponseContext> handler
        ) {
            partialResponseHandler = handler;
            return this;
        }

        @Override
        public TokenStream onPartialToolCallWithContext(
                BiConsumer<PartialToolCall, PartialToolCallContext> handler
        ) {
            return this;
        }

        @Override
        public TokenStream beforeToolExecution(
                Consumer<BeforeToolExecution> handler
        ) {
            return this;
        }

        @Override
        public TokenStream onRetrieved(Consumer<List<Content>> handler) {
            return this;
        }

        @Override
        public TokenStream onToolExecuted(Consumer<ToolExecution> handler) {
            return this;
        }

        @Override
        public TokenStream onCompleteResponse(Consumer<ChatResponse> handler) {
            return this;
        }

        @Override
        public TokenStream onError(Consumer<Throwable> handler) {
            return this;
        }

        @Override
        public TokenStream ignoreErrors() {
            return this;
        }

        @Override
        public void start() {
        }
    }

    private static final class ToolCallingTokenStream implements TokenStream {

        private final String toolName;
        private final String arguments;
        private final String resultText;
        private final Throwable failure;
        private Consumer<BeforeToolExecution> beforeToolExecution;
        private Consumer<ToolExecution> toolExecuted;
        private Consumer<ChatResponse> completeResponse;
        private Consumer<Throwable> errorHandler;

        private ToolCallingTokenStream() {
            this(
                    "web_search",
                    "{\"query\":\"latest news\"}",
                    "0123456789abcdefghijklmnopqrstuvwxyz",
                    null
            );
        }

        private ToolCallingTokenStream(
                String toolName,
                String arguments,
                String resultText
        ) {
            this(toolName, arguments, resultText, null);
        }

        private ToolCallingTokenStream(Throwable failure) {
            this("", "", "", failure);
        }

        private ToolCallingTokenStream(
                String toolName,
                String arguments,
                String resultText,
                Throwable failure
        ) {
            this.toolName = toolName;
            this.arguments = arguments;
            this.resultText = resultText;
            this.failure = failure;
        }

        @Override
        public TokenStream onPartialResponse(Consumer<String> handler) {
            return this;
        }

        @Override
        public TokenStream onPartialResponseWithContext(
                BiConsumer<PartialResponse, PartialResponseContext> handler
        ) {
            return this;
        }

        @Override
        public TokenStream onPartialToolCallWithContext(
                BiConsumer<PartialToolCall, PartialToolCallContext> handler
        ) {
            return this;
        }

        @Override
        public TokenStream beforeToolExecution(
                Consumer<BeforeToolExecution> handler
        ) {
            beforeToolExecution = handler;
            return this;
        }

        @Override
        public TokenStream onRetrieved(Consumer<List<Content>> handler) {
            return this;
        }

        @Override
        public TokenStream onToolExecuted(Consumer<ToolExecution> handler) {
            toolExecuted = handler;
            return this;
        }

        @Override
        public TokenStream onCompleteResponse(Consumer<ChatResponse> handler) {
            completeResponse = handler;
            return this;
        }

        @Override
        public TokenStream onError(Consumer<Throwable> handler) {
            errorHandler = handler;
            return this;
        }

        @Override
        public TokenStream ignoreErrors() {
            return this;
        }

        @Override
        public void start() {
            if (failure != null) {
                errorHandler.accept(failure);
                return;
            }
            ToolExecutionRequest request = ToolExecutionRequest.builder()
                    .id("tool-1")
                    .name(toolName)
                    .arguments(arguments)
                    .build();
            InvocationContext invocationContext = mock(InvocationContext.class);
            beforeToolExecution.accept(BeforeToolExecution.builder()
                    .request(request)
                    .invocationContext(invocationContext)
                    .build());

            LocalDateTime now = LocalDateTime.now();
            ToolExecutionResult result = ToolExecutionResult.builder()
                    .resultText(resultText)
                    .isError(false)
                    .build();
            toolExecuted.accept(ToolExecution.builder()
                    .request(request)
                    .result(result)
                    .startTime(now)
                    .finishTime(now.plusNanos(10_000_000))
                    .invocationContext(invocationContext)
                    .build());

            completeResponse.accept(ChatResponse.builder()
                    .aiMessage(AiMessage.from("final answer"))
                    .tokenUsage(new TokenUsage(1, 2))
                    .build());
        }
    }
}
