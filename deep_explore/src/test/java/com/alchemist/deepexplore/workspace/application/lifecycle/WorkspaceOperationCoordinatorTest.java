package com.alchemist.deepexplore.workspace.application.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class WorkspaceOperationCoordinatorTest {

    private static final String WORKSPACE_ID = "workspace-1";

    @Test
    void allowsReadersOfTheSameWorkspaceToRunConcurrently() throws Exception {
        WorkspaceOperationCoordinator coordinator =
                new WorkspaceOperationCoordinator();
        CountDownLatch entered = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximum = new AtomicInteger();

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> coordinator.withReadLock(
                    WORKSPACE_ID,
                    () -> concurrentAction(entered, release, active, maximum)
            ));
            var second = executor.submit(() -> coordinator.withReadLock(
                    WORKSPACE_ID,
                    () -> concurrentAction(entered, release, active, maximum)
            ));

            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(maximum).hasValue(2);
            release.countDown();
            assertThat(first.get(5, TimeUnit.SECONDS)).isEqualTo("done");
            assertThat(second.get(5, TimeUnit.SECONDS)).isEqualTo("done");
        }
    }

    @Test
    void writerWaitsUntilEveryReaderHasFinished() throws Exception {
        WorkspaceOperationCoordinator coordinator =
                new WorkspaceOperationCoordinator();
        CountDownLatch readerEntered = new CountDownLatch(1);
        CountDownLatch releaseReader = new CountDownLatch(1);
        AtomicBoolean writerEntered = new AtomicBoolean();

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var reader = executor.submit(() -> coordinator.withReadLock(
                    WORKSPACE_ID,
                    () -> {
                        readerEntered.countDown();
                        await(releaseReader);
                        return true;
                    }
            ));
            assertThat(readerEntered.await(5, TimeUnit.SECONDS)).isTrue();

            var writer = executor.submit(() -> coordinator.withWriteLock(
                    WORKSPACE_ID,
                    () -> writerEntered.compareAndSet(false, true)
            ));

            assertThat(waitUntilQueued(writerEntered, Duration.ofMillis(150)))
                    .isFalse();
            releaseReader.countDown();
            assertThat(reader.get(5, TimeUnit.SECONDS)).isTrue();
            assertThat(writer.get(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void operationsOnDifferentWorkspacesDoNotBlockEachOther() throws Exception {
        WorkspaceOperationCoordinator coordinator =
                new WorkspaceOperationCoordinator();
        CountDownLatch firstEntered = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> coordinator.withWriteLock(
                    "workspace-a",
                    () -> {
                        firstEntered.countDown();
                        await(releaseFirst);
                        return true;
                    }
            ));
            assertThat(firstEntered.await(5, TimeUnit.SECONDS)).isTrue();

            var second = executor.submit(() -> coordinator.withWriteLock(
                    "workspace-b",
                    () -> true
            ));
            assertThat(second.get(5, TimeUnit.SECONDS)).isTrue();
            releaseFirst.countDown();
            assertThat(first.get(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void writeOwnerMayCallNestedReadAndWriteOperations() {
        WorkspaceOperationCoordinator coordinator =
                new WorkspaceOperationCoordinator();

        String result = coordinator.withWriteLock(
                WORKSPACE_ID,
                () -> coordinator.withReadLock(
                        WORKSPACE_ID,
                        () -> coordinator.withWriteLock(
                                WORKSPACE_ID,
                                () -> "nested"
                        )
                )
        );

        assertThat(result).isEqualTo("nested");
    }

    private static String concurrentAction(
            CountDownLatch entered,
            CountDownLatch release,
            AtomicInteger active,
            AtomicInteger maximum
    ) {
        int current = active.incrementAndGet();
        maximum.accumulateAndGet(current, Math::max);
        entered.countDown();
        try {
            await(release);
            return "done";
        } finally {
            active.decrementAndGet();
        }
    }

    private static boolean waitUntilQueued(
            AtomicBoolean value,
            Duration duration
    ) throws InterruptedException {
        long deadline = System.nanoTime() + duration.toNanos();
        while (System.nanoTime() < deadline) {
            if (value.get()) {
                return true;
            }
            Thread.sleep(5);
        }
        return value.get();
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting for test latch");
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Test was interrupted", error);
        }
    }
}
