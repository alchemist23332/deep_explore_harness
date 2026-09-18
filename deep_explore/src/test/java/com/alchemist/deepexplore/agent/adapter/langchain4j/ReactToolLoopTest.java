package com.alchemist.deepexplore.agent.adapter.langchain4j;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

import com.alchemist.deepexplore.agent.adapter.langchain4j.tool.WebSearchToolAdapter;
import com.alchemist.deepexplore.agent.adapter.langchain4j.tool.WebSearchProviderClient;
import com.alchemist.deepexplore.agent.adapter.langchain4j.tool.WebSearchRoutingContext;
import com.alchemist.deepexplore.agent.domain.WebSearchProvider;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.service.AiServices;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ReactToolLoopTest {

    @Test
    void executesToolAndFeedsResultBackToModel() {
        ScriptedStreamingModel model = new ScriptedStreamingModel();
        RecordingSearchClient searchClient = new RecordingSearchClient();
        WebSearchToolAdapter tool = new WebSearchToolAdapter(
                List.of(searchClient),
                WebSearchProvider.JINA,
                new WebSearchRoutingContext(),
                400
        );
        StreamingAssistant assistant = AiServices.builder(StreamingAssistant.class)
                .streamingChatModel(model)
                .chatMemoryProvider(id -> MessageWindowChatMemory.builder()
                        .id(id)
                        .maxMessages(20)
                        .build())
                .tools(tool)
                .maxToolCallingRoundTrips(3)
                .build();

        List<String> lifecycle = new ArrayList<>();
        AtomicReference<ChatResponse> finalResponse = new AtomicReference<>();
        assistant.chat("conversation-1", "What changed in DeepSeek V4?")
                .beforeToolExecution(toolCall ->
                        lifecycle.add("start:" + toolCall.request().name()))
                .onToolExecuted(toolExecution ->
                        lifecycle.add("end:" + toolExecution.request().name()))
                .onCompleteResponse(finalResponse::set)
                .onError(error -> fail("Unexpected ReAct error", error))
                .start();

        assertThat(model.invocations).isEqualTo(2);
        assertThat(model.secondRequest.messages())
                .anyMatch(ToolExecutionResultMessage.class::isInstance);
        assertThat(searchClient.query).isEqualTo("DeepSeek V4 latest changes");
        assertThat(lifecycle).containsExactly(
                "start:web_search",
                "end:web_search"
        );
        assertThat(finalResponse.get().aiMessage().text())
                .isEqualTo("DeepSeek V4 has current updates [source].");
    }

    @Test
    void countsMultipleToolCallsInOneModelResponseAsOneRound() {
        BatchStreamingModel model = new BatchStreamingModel(12);
        CountingSearchClient searchClient = new CountingSearchClient();
        WebSearchToolAdapter tool = new WebSearchToolAdapter(
                List.of(searchClient),
                WebSearchProvider.JINA,
                new WebSearchRoutingContext(),
                400
        );
        StreamingAssistant assistant = AiServices.builder(StreamingAssistant.class)
                .streamingChatModel(model)
                .chatMemoryProvider(id -> MessageWindowChatMemory.builder()
                        .id(id)
                        .maxMessages(50)
                        .build())
                .tools(tool)
                .maxToolCallingRoundTrips(1)
                .build();

        List<String> lifecycle = new ArrayList<>();
        AtomicReference<ChatResponse> finalResponse = new AtomicReference<>();
        assistant.chat("conversation-1", "Research several topics")
                .beforeToolExecution(toolCall -> lifecycle.add("start"))
                .onToolExecuted(toolExecution -> lifecycle.add("end"))
                .onCompleteResponse(finalResponse::set)
                .onError(error -> fail("Unexpected round-limit error", error))
                .start();

        assertThat(model.invocations).isEqualTo(2);
        assertThat(model.secondRequest.messages().stream()
                .filter(ToolExecutionResultMessage.class::isInstance))
                .hasSize(12);
        assertThat(searchClient.calls).isEqualTo(12);
        assertThat(lifecycle).hasSize(24);
        assertThat(finalResponse.get().aiMessage().text())
                .isEqualTo("batch complete");
    }

    @Test
    void rejectsNextToolRoundBeforeItsToolSideEffect() {
        LoopingToolModel model = new LoopingToolModel();
        CountingSearchClient searchClient = new CountingSearchClient();
        WebSearchToolAdapter tool = new WebSearchToolAdapter(
                List.of(searchClient),
                WebSearchProvider.JINA,
                new WebSearchRoutingContext(),
                400
        );
        StreamingAssistant assistant = AiServices.builder(StreamingAssistant.class)
                .streamingChatModel(model)
                .chatMemoryProvider(id -> MessageWindowChatMemory.builder()
                        .id(id)
                        .maxMessages(20)
                        .build())
                .tools(tool)
                .maxToolCallingRoundTrips(1)
                .build();
        var stream = assistant.chat("conversation-1", "Keep searching")
                .onCompleteResponse(response -> fail(
                        "The second tool round should have been rejected"
                ))
                .onError(ignored -> {
                    // LangChain4j also rethrows synchronous round-limit errors.
                });

        assertThatThrownBy(stream::start)
                .hasMessageContaining("maxToolCallingRoundTrips");

        assertThat(model.invocations).isEqualTo(2);
        assertThat(searchClient.calls).isEqualTo(1);
    }

    private static final class ScriptedStreamingModel
            implements StreamingChatModel {

        private int invocations;
        private ChatRequest secondRequest;

        @Override
        public void doChat(
                ChatRequest request,
                StreamingChatResponseHandler handler
        ) {
            invocations++;
            assertThat(request.toolSpecifications())
                    .extracting(specification -> specification.name())
                    .containsExactly("web_search");

            if (invocations == 1) {
                ToolExecutionRequest toolRequest = ToolExecutionRequest.builder()
                        .id("tool-1")
                        .name("web_search")
                        .arguments(
                                "{\"query\":\"DeepSeek V4 latest changes\"}"
                        )
                        .build();
                handler.onCompleteResponse(ChatResponse.builder()
                        .aiMessage(AiMessage.builder()
                                .toolExecutionRequests(List.of(toolRequest))
                                .build())
                        .build());
                return;
            }

            secondRequest = request;
            handler.onPartialResponse(
                    "DeepSeek V4 has current updates [source]."
            );
            handler.onCompleteResponse(ChatResponse.builder()
                    .aiMessage(AiMessage.from(
                            "DeepSeek V4 has current updates [source]."
                    ))
                    .build());
        }
    }

    private static final class RecordingSearchClient
            implements WebSearchProviderClient {

        private String query;

        @Override
        public WebSearchProvider provider() {
            return WebSearchProvider.JINA;
        }

        @Override
        public String search(String query) {
            this.query = query;
            return "DeepSeek release notes: https://example.test/source";
        }
    }

    private static final class BatchStreamingModel
            implements StreamingChatModel {

        private final int batchSize;
        private int invocations;
        private ChatRequest secondRequest;

        private BatchStreamingModel(int batchSize) {
            this.batchSize = batchSize;
        }

        @Override
        public void doChat(
                ChatRequest request,
                StreamingChatResponseHandler handler
        ) {
            invocations++;
            if (invocations == 1) {
                List<ToolExecutionRequest> requests = new ArrayList<>();
                for (int index = 0; index < batchSize; index++) {
                    requests.add(ToolExecutionRequest.builder()
                            .id("tool-" + index)
                            .name("web_search")
                            .arguments("{\"query\":\"topic " + index + "\"}")
                            .build());
                }
                handler.onCompleteResponse(ChatResponse.builder()
                        .aiMessage(AiMessage.builder()
                                .toolExecutionRequests(requests)
                                .build())
                        .build());
                return;
            }

            secondRequest = request;
            handler.onCompleteResponse(ChatResponse.builder()
                    .aiMessage(AiMessage.from("batch complete"))
                    .build());
        }
    }

    private static final class CountingSearchClient
            implements WebSearchProviderClient {

        private int calls;

        @Override
        public WebSearchProvider provider() {
            return WebSearchProvider.JINA;
        }

        @Override
        public String search(String query) {
            calls++;
            return "result for " + query;
        }
    }

    private static final class LoopingToolModel
            implements StreamingChatModel {

        private int invocations;

        @Override
        public void doChat(
                ChatRequest request,
                StreamingChatResponseHandler handler
        ) {
            invocations++;
            ToolExecutionRequest toolRequest = ToolExecutionRequest.builder()
                    .id("tool-" + invocations)
                    .name("web_search")
                    .arguments("{\"query\":\"topic " + invocations + "\"}")
                    .build();
            handler.onCompleteResponse(ChatResponse.builder()
                    .aiMessage(AiMessage.builder()
                            .toolExecutionRequests(List.of(toolRequest))
                            .build())
                    .build());
        }
    }
}
