package com.alchemist.deepexplore.agent.adapter.langchain4j.tool.execution;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.CompleteToolCall;
import dev.langchain4j.model.chat.response.PartialResponse;
import dev.langchain4j.model.chat.response.PartialResponseContext;
import dev.langchain4j.model.chat.response.PartialThinking;
import dev.langchain4j.model.chat.response.PartialThinkingContext;
import dev.langchain4j.model.chat.response.PartialToolCall;
import dev.langchain4j.model.chat.response.PartialToolCallContext;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Delays complete tool-call notifications until the whole model response is
 * available, allowing a deterministic barrier plan to be registered first.
 */
public final class BarrierAwareStreamingChatModel
        implements StreamingChatModel {

    private final StreamingChatModel delegate;
    private final ToolBatchRegistry batches;

    public BarrierAwareStreamingChatModel(
            StreamingChatModel delegate,
            ToolBatchRegistry batches
    ) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.batches = Objects.requireNonNull(batches, "batches");
    }

    @Override
    public void doChat(
            ChatRequest request,
            StreamingChatResponseHandler handler
    ) {
        delegate.doChat(request, new BufferingHandler(handler));
    }

    @Override
    public ChatRequestParameters defaultRequestParameters() {
        return delegate.defaultRequestParameters();
    }

    @Override
    public List<ChatModelListener> listeners() {
        return delegate.listeners();
    }

    @Override
    public ModelProvider provider() {
        return delegate.provider();
    }

    @Override
    public Set<Capability> supportedCapabilities() {
        return delegate.supportedCapabilities();
    }

    private final class BufferingHandler
            implements StreamingChatResponseHandler {

        private final StreamingChatResponseHandler downstream;

        private BufferingHandler(StreamingChatResponseHandler downstream) {
            this.downstream = downstream;
        }

        @Override
        public void onPartialResponse(String partialResponse) {
            downstream.onPartialResponse(partialResponse);
        }

        @Override
        public void onPartialResponse(
                PartialResponse partialResponse,
                PartialResponseContext context
        ) {
            downstream.onPartialResponse(partialResponse, context);
        }

        @Override
        public void onPartialThinking(PartialThinking partialThinking) {
            downstream.onPartialThinking(partialThinking);
        }

        @Override
        public void onPartialThinking(
                PartialThinking partialThinking,
                PartialThinkingContext context
        ) {
            downstream.onPartialThinking(partialThinking, context);
        }

        @Override
        public void onPartialToolCall(PartialToolCall partialToolCall) {
            downstream.onPartialToolCall(partialToolCall);
        }

        @Override
        public void onPartialToolCall(
                PartialToolCall partialToolCall,
                PartialToolCallContext context
        ) {
            downstream.onPartialToolCall(partialToolCall, context);
        }

        @Override
        public void onCompleteToolCall(CompleteToolCall completeToolCall) {
            // The complete ordered list is emitted from onCompleteResponse.
        }

        @Override
        public void onUnmappedRawEvent(Object event) {
            downstream.onUnmappedRawEvent(event);
        }

        @Override
        public void onCompleteResponse(ChatResponse response) {
            if (!response.aiMessage().hasToolExecutionRequests()) {
                downstream.onCompleteResponse(response);
                return;
            }

            List<ToolExecutionRequest> requests =
                    response.aiMessage().toolExecutionRequests();
            ToolBatchRegistry.BatchHandle handle;
            try {
                handle = batches.register(requests);
            } catch (RuntimeException | Error error) {
                downstream.onError(error);
                return;
            }
            try {
                for (int index = 0; index < requests.size(); index++) {
                    downstream.onCompleteToolCall(new CompleteToolCall(
                            index,
                            requests.get(index)
                    ));
                }
                downstream.onCompleteResponse(response);
            } catch (RuntimeException | Error error) {
                batches.cancelBatch(handle);
                throw error;
            }
        }

        @Override
        public void onError(Throwable error) {
            downstream.onError(error);
        }
    }
}
