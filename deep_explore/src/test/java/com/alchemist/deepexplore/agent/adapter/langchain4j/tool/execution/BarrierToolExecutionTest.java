package com.alchemist.deepexplore.agent.adapter.langchain4j.tool.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

import com.alchemist.deepexplore.agent.adapter.langchain4j.StreamingAssistant;
import com.alchemist.deepexplore.agent.adapter.langchain4j.tool.CompositeToolProvider;
import com.alchemist.deepexplore.agent.adapter.langchain4j.tool.LangChainToolProvider;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.CompleteToolCall;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.tool.AiServiceTool;
import dev.langchain4j.service.tool.DefaultToolExecutor;
import dev.langchain4j.service.tool.ToolProviderRequest;
import dev.langchain4j.service.tool.ToolProviderResult;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class BarrierToolExecutionTest {

    @Test
    void runsReadsInParallelAndPreservesWriteBarriers() throws Exception {
        ExecutionRecorder recorder = new ExecutionRecorder();
        TestToolProvider provider = new TestToolProvider(
                new BarrierTestTools(recorder)
        );
        ToolPolicyRegistry policies = new ToolPolicyRegistry(List.of(provider));
        ToolBatchRegistry batches = new ToolBatchRegistry(
                policies,
                properties(4, 16)
        );
        CompositeToolProvider composite = new CompositeToolProvider(
                List.of(provider),
                policies,
                batches
        );
        List<ToolExecutionRequest> requests = List.of(
                request("call-1", "read_value", "before-1"),
                request("call-2", "read_value", "before-2"),
                request("call-3", "write_value", "write"),
                request("call-4", "read_value", "after")
        );
        ScriptedBatchModel rawModel = new ScriptedBatchModel(requests, true);

        try (ExecutorService executor = virtualExecutor()) {
            StreamingAssistant assistant = AiServices.builder(
                            StreamingAssistant.class
                    )
                    .streamingChatModel(new BarrierAwareStreamingChatModel(
                            rawModel,
                            batches
                    ))
                    .chatMemoryProvider(id -> MessageWindowChatMemory.builder()
                            .id(id)
                            .maxMessages(20)
                            .build())
                    .toolProvider(composite)
                    .executeToolsConcurrently(executor)
                    .maxToolCallingRoundTrips(1)
                    .build();

            AtomicBoolean completed = new AtomicBoolean();
            assistant.chat("run-1", "execute the batch")
                    .onCompleteResponse(response -> completed.set(true))
                    .onError(error -> fail("Unexpected tool-loop error", error))
                    .start();

            assertThat(completed).isTrue();
            assertThat(recorder.maxConcurrentReads()).isEqualTo(2);
            assertThat(recorder.writeObservedActiveReads()).isZero();
            assertThat(recorder.afterReadObservedWrite()).isTrue();
            assertThat(recorder.writeCalls()).isEqualTo(1);
            assertThat(recorder.readCalls()).isEqualTo(3);
            assertThat(rawModel.secondRequest.messages().stream()
                    .filter(ToolExecutionResultMessage.class::isInstance)
                    .map(ToolExecutionResultMessage.class::cast)
                    .map(ToolExecutionResultMessage::id))
                    .containsExactly("call-1", "call-2", "call-3", "call-4");
        }
    }

    @Test
    void releasesBarrierAfterToolFailure() {
        ExecutionRecorder recorder = new ExecutionRecorder();
        TestToolProvider provider = new TestToolProvider(
                new BarrierTestTools(recorder)
        );
        ToolPolicyRegistry policies = new ToolPolicyRegistry(List.of(provider));
        ToolBatchRegistry batches = new ToolBatchRegistry(
                policies,
                properties(2, 16)
        );
        CompositeToolProvider composite = new CompositeToolProvider(
                List.of(provider),
                policies,
                batches
        );
        ScriptedBatchModel rawModel = new ScriptedBatchModel(List.of(
                request("call-1", "failing_read", "failure"),
                request("call-2", "write_value", "write")
        ), false);

        try (ExecutorService executor = virtualExecutor()) {
            StreamingAssistant assistant = AiServices.builder(
                            StreamingAssistant.class
                    )
                    .streamingChatModel(new BarrierAwareStreamingChatModel(
                            rawModel,
                            batches
                    ))
                    .chatMemoryProvider(id -> MessageWindowChatMemory.builder()
                            .id(id)
                            .maxMessages(20)
                            .build())
                    .toolProvider(composite)
                    .executeToolsConcurrently(executor)
                    .maxToolCallingRoundTrips(1)
                    .build();

            assistant.chat("run-2", "continue after failure")
                    .onCompleteResponse(ignored -> {
                    })
                    .onError(error -> fail("Failure should be returned to the model", error))
                    .start();

            assertThat(recorder.writeCalls()).isEqualTo(1);
            assertThat(rawModel.secondRequest.messages().stream()
                    .filter(ToolExecutionResultMessage.class::isInstance))
                    .hasSize(2);
        }
    }

    @Test
    void rejectsOversizedBatchBeforeAnyToolSideEffect() {
        TestToolProvider provider = new TestToolProvider(
                new BarrierTestTools(new ExecutionRecorder())
        );
        ToolPolicyRegistry policies = new ToolPolicyRegistry(List.of(provider));
        ToolBatchRegistry batches = new ToolBatchRegistry(
                policies,
                properties(2, 2)
        );

        assertThatThrownBy(() -> batches.register(List.of(
                request("call-1", "read_value", "1"),
                request("call-2", "read_value", "2"),
                request("call-3", "read_value", "3")
        )))
                .isInstanceOf(ToolSchedulingException.class)
                .extracting(error -> ((ToolSchedulingException) error).code())
                .isEqualTo("TOOL_BATCH_TOO_LARGE");
    }

    @Test
    void cancellationPreventsWaitingBarrierSideEffect() throws Exception {
        TestToolProvider provider = new TestToolProvider(
                new BarrierTestTools(new ExecutionRecorder())
        );
        ToolPolicyRegistry policies = new ToolPolicyRegistry(List.of(provider));
        ToolBatchRegistry batches = new ToolBatchRegistry(
                policies,
                properties(2, 16)
        );
        batches.register(List.of(
                request("call-1", "read_value", "read"),
                request("call-2", "write_value", "write")
        ));
        CountDownLatch readStarted = new CountDownLatch(1);
        CountDownLatch releaseRead = new CountDownLatch(1);
        AtomicBoolean writeRan = new AtomicBoolean();

        try (ExecutorService executor = virtualExecutor()) {
            CompletableFuture<String> read = CompletableFuture.supplyAsync(
                    () -> batches.execute(
                            request("call-1", "read_value", "read"),
                            "run-cancel",
                            () -> {
                                readStarted.countDown();
                                await(releaseRead);
                                return "read";
                            }
                    ),
                    executor
            );
            CompletableFuture<String> write = CompletableFuture.supplyAsync(
                    () -> batches.execute(
                            request("call-2", "write_value", "write"),
                            "run-cancel",
                            () -> {
                                writeRan.set(true);
                                return "write";
                            }
                    ),
                    executor
            );

            assertThat(readStarted.await(2, TimeUnit.SECONDS)).isTrue();
            batches.cancelRun("run-cancel");
            releaseRead.countDown();
            assertThat(read.join()).isEqualTo("read");
            assertThatThrownBy(write::join)
                    .hasRootCauseInstanceOf(ToolSchedulingException.class);
            assertThat(writeRan).isFalse();
        }
    }

    @Test
    void capsParallelReadExecution() {
        TestToolProvider provider = new TestToolProvider(
                new BarrierTestTools(new ExecutionRecorder())
        );
        ToolPolicyRegistry policies = new ToolPolicyRegistry(List.of(provider));
        ToolBatchRegistry batches = new ToolBatchRegistry(
                policies,
                properties(2, 16)
        );
        List<ToolExecutionRequest> requests = List.of(
                request("call-1", "read_value", "1"),
                request("call-2", "read_value", "2"),
                request("call-3", "read_value", "3"),
                request("call-4", "read_value", "4")
        );
        batches.register(requests);
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximum = new AtomicInteger();

        try (ExecutorService executor = virtualExecutor()) {
            List<CompletableFuture<String>> futures = requests.stream()
                    .map(request -> CompletableFuture.supplyAsync(
                            () -> batches.execute(
                                    request,
                                    "run-cap",
                                    () -> {
                                        int current = active.incrementAndGet();
                                        maximum.accumulateAndGet(
                                                current,
                                                Math::max
                                        );
                                        try {
                                            Thread.sleep(50);
                                            return request.id();
                                        } catch (InterruptedException error) {
                                            Thread.currentThread().interrupt();
                                            throw new IllegalStateException(error);
                                        } finally {
                                            active.decrementAndGet();
                                        }
                                    }
                            ),
                            executor
                    ))
                    .toList();
            futures.forEach(CompletableFuture::join);
        }

        assertThat(maximum).hasValue(2);
    }

    private static ToolExecutionProperties properties(
            int maxParallelism,
            int maxCallsPerRound
    ) {
        return new ToolExecutionProperties(
                ToolExecutionProperties.Mode.BARRIER,
                maxParallelism,
                maxCallsPerRound,
                Duration.ofSeconds(5)
        );
    }

    private static ExecutorService virtualExecutor() {
        return Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual().factory()
        );
    }

    private static ToolExecutionRequest request(
            String id,
            String name,
            String value
    ) {
        return ToolExecutionRequest.builder()
                .id(id)
                .name(name)
                .arguments("{\"value\":\"" + value + "\"}")
                .build();
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(2, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting in test tool");
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(error);
        }
    }

    private static final class TestToolProvider
            implements LangChainToolProvider {

        private static final Map<String, ToolExecutionPolicy> POLICIES = Map.of(
                "read_value",
                ToolExecutionPolicy.readOnly(ToolResource.WORKSPACE),
                "failing_read",
                ToolExecutionPolicy.readOnly(ToolResource.WORKSPACE),
                "write_value",
                ToolExecutionPolicy.mutation(ToolResource.WORKSPACE)
        );

        private final List<AiServiceTool> tools;

        private TestToolProvider(BarrierTestTools target) {
            Map<String, Method> methods = Arrays.stream(
                            BarrierTestTools.class.getDeclaredMethods()
                    )
                    .filter(method -> method.isAnnotationPresent(Tool.class))
                    .collect(java.util.stream.Collectors.toMap(
                            method -> method.getAnnotation(Tool.class).name(),
                            method -> method
                    ));
            this.tools = ToolSpecifications.toolSpecificationsFrom(target)
                    .stream()
                    .map(specification -> AiServiceTool.builder()
                            .toolSpecification(specification)
                            .toolExecutor(new DefaultToolExecutor(
                                    target,
                                    methods.get(specification.name())
                            ))
                            .build())
                    .toList();
        }

        @Override
        public ToolProviderResult provideTools(ToolProviderRequest request) {
            return ToolProviderResult.builder().addAll(tools).build();
        }

        @Override
        public Map<String, ToolExecutionPolicy> toolPolicies() {
            return POLICIES;
        }
    }

    private static final class BarrierTestTools {

        private final ExecutionRecorder recorder;

        private BarrierTestTools(ExecutionRecorder recorder) {
            this.recorder = recorder;
        }

        @Tool(name = "read_value")
        public String readValue(@P(name = "value") String value) {
            return recorder.read(value);
        }

        @Tool(name = "failing_read")
        public String failingRead(@P(name = "value") String value) {
            throw new IllegalStateException("expected read failure: " + value);
        }

        @Tool(name = "write_value")
        public String writeValue(@P(name = "value") String value) {
            return recorder.write(value);
        }
    }

    private static final class ExecutionRecorder {

        private final CountDownLatch beforeReadsStarted = new CountDownLatch(2);
        private final AtomicInteger activeReads = new AtomicInteger();
        private final AtomicInteger maxConcurrentReads = new AtomicInteger();
        private final AtomicInteger readCalls = new AtomicInteger();
        private final AtomicInteger writeCalls = new AtomicInteger();
        private final AtomicInteger writeObservedActiveReads = new AtomicInteger(-1);
        private final AtomicBoolean written = new AtomicBoolean();
        private final AtomicBoolean afterReadObservedWrite = new AtomicBoolean();

        private String read(String value) {
            readCalls.incrementAndGet();
            int active = activeReads.incrementAndGet();
            maxConcurrentReads.accumulateAndGet(active, Math::max);
            try {
                if (value.startsWith("before-")) {
                    beforeReadsStarted.countDown();
                    await(beforeReadsStarted);
                }
                if ("after".equals(value)) {
                    afterReadObservedWrite.set(written.get());
                }
                return value;
            } finally {
                activeReads.decrementAndGet();
            }
        }

        private String write(String value) {
            writeCalls.incrementAndGet();
            writeObservedActiveReads.set(activeReads.get());
            written.set(true);
            return value;
        }

        private int maxConcurrentReads() {
            return maxConcurrentReads.get();
        }

        private int readCalls() {
            return readCalls.get();
        }

        private int writeCalls() {
            return writeCalls.get();
        }

        private int writeObservedActiveReads() {
            return writeObservedActiveReads.get();
        }

        private boolean afterReadObservedWrite() {
            return afterReadObservedWrite.get();
        }
    }

    private static final class ScriptedBatchModel
            implements StreamingChatModel {

        private final List<ToolExecutionRequest> requests;
        private final boolean emitCompleteToolCallbacks;
        private int invocations;
        private ChatRequest secondRequest;

        private ScriptedBatchModel(
                List<ToolExecutionRequest> requests,
                boolean emitCompleteToolCallbacks
        ) {
            this.requests = new ArrayList<>(requests);
            this.emitCompleteToolCallbacks = emitCompleteToolCallbacks;
        }

        @Override
        public void doChat(
                ChatRequest request,
                StreamingChatResponseHandler handler
        ) {
            invocations++;
            if (invocations == 1) {
                if (emitCompleteToolCallbacks) {
                    for (int index = 0; index < requests.size(); index++) {
                        handler.onCompleteToolCall(new CompleteToolCall(
                                index,
                                requests.get(index)
                        ));
                    }
                }
                handler.onCompleteResponse(ChatResponse.builder()
                        .aiMessage(AiMessage.from(requests))
                        .build());
                return;
            }
            secondRequest = request;
            handler.onCompleteResponse(ChatResponse.builder()
                    .aiMessage(AiMessage.from("complete"))
                    .build());
        }
    }
}
