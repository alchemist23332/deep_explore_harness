package com.alchemist.deepexplore.agent.adapter.langchain4j;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.alchemist.deepexplore.agent.adapter.langchain4j.memory.LangChain4jMemoryManager;
import com.alchemist.deepexplore.agent.adapter.langchain4j.tool.WebSearchRoutingContext;
import com.alchemist.deepexplore.agent.domain.AgentExecutionEvent;
import com.alchemist.deepexplore.agent.domain.AgentExecutionRequest;
import com.alchemist.deepexplore.agent.domain.AgentProfile;
import com.alchemist.deepexplore.agent.domain.WebSearchProvider;
import com.alchemist.deepexplore.config.AiModelProperties;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.PartialResponse;
import dev.langchain4j.model.chat.response.PartialResponseContext;
import dev.langchain4j.model.chat.response.PartialToolCall;
import dev.langchain4j.model.chat.response.PartialToolCallContext;
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
import reactor.test.StepVerifier;

class LangChain4jAgentExecutorTest {

    @Test
    void emitsToolLifecycleAndFinalResponse() {
        StreamingAssistant fastAssistant = mock(StreamingAssistant.class);
        StreamingAssistant deepAssistant = mock(StreamingAssistant.class);
        when(fastAssistant.chat("conversation-1", "latest news"))
                .thenReturn(new ToolCallingTokenStream());
        WebSearchRoutingContext routingContext =
                mock(WebSearchRoutingContext.class);

        LangChain4jAgentExecutor executor = new LangChain4jAgentExecutor(
                "assistant",
                fastAssistant,
                deepAssistant,
                properties(),
                mock(LangChain4jMemoryManager.class),
                routingContext,
                12
        );

        StepVerifier.create(executor.execute(new AgentExecutionRequest(
                        "run-1",
                        "conversation-1",
                        "assistant",
                        AgentProfile.FAST.id(),
                        "latest news",
                        WebSearchProvider.TAVILY
                )))
                .assertNext(event -> {
                    var started = (AgentExecutionEvent.ToolCallStarted) event;
                    assertThat(started.toolCallId()).isEqualTo("tool-1");
                    assertThat(started.toolName()).isEqualTo("web_search");
                })
                .assertNext(event -> {
                    var completed = (AgentExecutionEvent.ToolCallCompleted) event;
                    assertThat(completed.success()).isTrue();
                    assertThat(completed.result())
                            .startsWith("0123456789ab")
                            .contains("truncated for run event");
                })
                .assertNext(event -> {
                    var completed = (AgentExecutionEvent.Completed) event;
                    assertThat(completed.text()).isEqualTo("final answer");
                    assertThat(completed.model()).isEqualTo("deepseek-v4-flash");
                    assertThat(completed.tokenUsage()).isEqualTo(3);
                })
                .verifyComplete();

        verify(routingContext).bind(
                "conversation-1",
                WebSearchProvider.TAVILY
        );
        verify(routingContext).clear("conversation-1");
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

    private static final class ToolCallingTokenStream implements TokenStream {

        private Consumer<BeforeToolExecution> beforeToolExecution;
        private Consumer<ToolExecution> toolExecuted;
        private Consumer<ChatResponse> completeResponse;

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
            return this;
        }

        @Override
        public TokenStream ignoreErrors() {
            return this;
        }

        @Override
        public void start() {
            ToolExecutionRequest request = ToolExecutionRequest.builder()
                    .id("tool-1")
                    .name("web_search")
                    .arguments("{\"query\":\"latest news\"}")
                    .build();
            InvocationContext invocationContext = mock(InvocationContext.class);
            beforeToolExecution.accept(BeforeToolExecution.builder()
                    .request(request)
                    .invocationContext(invocationContext)
                    .build());

            LocalDateTime now = LocalDateTime.now();
            ToolExecutionResult result = ToolExecutionResult.builder()
                    .resultText("0123456789abcdefghijklmnopqrstuvwxyz")
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
