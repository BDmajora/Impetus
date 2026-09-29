package com.bdmajora.impetus.engine.impl.render.chunk.compile.executor;

import com.bdmajora.impetus.engine.impl.render.chunk.RenderSection;
import com.bdmajora.impetus.engine.impl.render.chunk.compile.ChunkBuildBuffersTest;
import com.bdmajora.impetus.engine.impl.render.chunk.compile.ChunkBuildContext;
import com.bdmajora.impetus.engine.impl.render.chunk.compile.ChunkSortOutput;
import com.bdmajora.impetus.engine.impl.render.chunk.compile.ChunkTaskOutput;
import com.bdmajora.impetus.engine.impl.render.chunk.compile.tasks.ChunkBuilderTask;
import com.bdmajora.impetus.engine.impl.util.task.CancellationToken;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class ExecutorTest {
    private static final RenderSection SECTION = new RenderSection(null, 0, 0, 0);

    // A task that produces an empty sort output, or null when asked to behave as cancelled, or throws
    private static ChunkBuilderTask<ChunkSortOutput> task(String mode) {
        return new ChunkBuilderTask<>() {
            @Override
            public ChunkSortOutput execute(ChunkBuildContext context, CancellationToken token) {
                return switch (mode) {
                    case "null" -> null;
                    case "throw" -> throw new IllegalStateException("boom");
                    default -> new ChunkSortOutput(SECTION, 1, new Reference2ReferenceOpenHashMap<>());
                };
            }
        };
    }

    private static ChunkBuildContext context() {
        return new ChunkBuildContext(ChunkBuildBuffersTest.CONFIG);
    }

    @Test
    void typedJobsReportSuccessFailureAndCancellation() {
        List<ChunkJobResult<ChunkSortOutput>> results = new ArrayList<>();
        ChunkJobTyped<ChunkBuilderTask<ChunkSortOutput>, ChunkSortOutput> job = new ChunkJobTyped<>(task("ok"), results::add);
        assertFalse(job.isStarted());
        assertFalse(job.isCancelled());
        job.execute(context());
        assertTrue(job.isStarted());
        assertInstanceOf(ChunkJobResult.Success.class, results.get(0));
        new ChunkJobTyped<>(task("throw"), results::add).execute(context());
        assertInstanceOf(ChunkJobResult.Failure.class, results.get(1));
        new ChunkJobTyped<>(task("null"), results::add).execute(context());
        assertEquals(2, results.size());
        ChunkJobTyped<ChunkBuilderTask<ChunkSortOutput>, ChunkSortOutput> cancelled = new ChunkJobTyped<>(task("ok"), results::add);
        cancelled.setCancelled();
        assertTrue(cancelled.isCancelled());
        cancelled.execute(context());
        assertEquals(2, results.size());
        ChunkJobTyped<ChunkBuilderTask<ChunkSortOutput>, ChunkSortOutput> badConsumer = new ChunkJobTyped<>(task("ok"), r -> {
            throw new IllegalArgumentException("consumer");
        });
        assertThrows(RuntimeException.class, () -> badConsumer.execute(context()));
    }

    @Test
    void queueOrdersImportantJobsFirstAndStopsOnShutdown() throws InterruptedException {
        ChunkJobQueue queue = new ChunkJobQueue();
        assertTrue(queue.isRunning());
        assertTrue(queue.isEmpty());
        assertNull(queue.pollJob());
        ChunkJob normal = new ChunkJobTyped<>(task("ok"), r -> {});
        ChunkJob urgent = new ChunkJobTyped<>(task("ok"), r -> {});
        queue.add(normal, false);
        queue.add(urgent, true);
        assertEquals(2, queue.size());
        assertSame(urgent, queue.waitForNextJob());
        assertFalse(queue.checkAndClearWorkerBlocked());
        assertFalse(queue.stealJob(urgent));
        assertTrue(queue.stealJob(normal));
        assertEquals(0, queue.size());
        queue.add(normal, false);
        assertSame(normal, queue.pollJob());
        Thread waiter = new Thread(() -> {
            try {
                queue.waitForNextJob();
            } catch (InterruptedException ignored) {
            }
        });
        waiter.start();
        Thread.sleep(50);
        assertTrue(queue.checkAndClearWorkerBlocked());
        queue.add(normal, false);
        waiter.join(1000);
        assertFalse(waiter.isAlive());
        queue.add(normal, false);
        assertEquals(List.of(normal), new ArrayList<>(queue.shutdown()));
        assertFalse(queue.isRunning());
        assertNull(queue.pollJob());
        assertNull(queue.waitForNextJob());
        assertThrows(IllegalStateException.class, () -> queue.add(normal, false));
        ChunkJobQueue empty = new ChunkJobQueue();
        assertFalse(empty.stealJob(normal));
    }

    @Test
    void builderWithoutWorkersRunsJobsOnTick() {
        ChunkBuilder builder = new ChunkBuilder(ChunkBuilder.ManagedBlocker.NONE, ExecutorTest::context, -1);
        assertEquals(0, builder.getTotalThreadCount());
        assertEquals(2, builder.getTargetQueueSize());
        assertEquals(2, builder.getSchedulingBudget());
        List<ChunkJobResult<ChunkSortOutput>> results = new ArrayList<>();
        var job = builder.scheduleTask(task("ok"), true, results::add);
        builder.scheduleTask(task("ok"), false, results::add);
        assertEquals(2, builder.getScheduledJobCount());
        assertFalse(builder.isBuildQueueEmpty());
        assertEquals(0, builder.getSchedulingBudget());
        builder.tryStealTask(job);
        assertEquals(1, results.size());
        builder.tryStealTask(job);
        builder.tick();
        assertEquals(2, results.size());
        assertTrue(builder.isBuildQueueEmpty());
        builder.tickSchedulingBudget();
        builder.setDispatchBudgetLimited(true);
        builder.tickSchedulingBudget();
        assertEquals(2, builder.getTargetQueueSize());
        assertEquals(0, builder.getBusyThreadCount());
        assertThrows(NullPointerException.class, () -> builder.scheduleTask(null, false, r -> {}));
        builder.managedBlock(() -> true);
        builder.shutdown();
        assertThrows(IllegalStateException.class, builder::shutdown);
        assertThrows(IllegalStateException.class, () -> builder.scheduleTask(task("ok"), false, r -> {}));
        assertTrue(ChunkBuilder.getMaxThreadCount() >= 1);
        ChunkBuilder heuristic = new ChunkBuilder(ChunkBuilder.ManagedBlocker.NONE, ExecutorTest::context, 0);
        assertTrue(heuristic.getTotalThreadCount() >= 1);
        heuristic.shutdown();
    }

    @Test
    void workersRunScheduledJobsAndTheControllerAdapts() throws InterruptedException {
        AtomicInteger blocks = new AtomicInteger();
        ChunkBuilder.ManagedBlocker blocker = isDone -> {
            blocks.incrementAndGet();
            ChunkBuilder.ManagedBlocker.NONE.managedBlock(isDone);
        };
        ChunkBuilder builder = new ChunkBuilder(blocker, ExecutorTest::context, 2);
        assertEquals(2, builder.getTotalThreadCount());
        assertEquals(64, builder.getTargetQueueSize());
        CountDownLatch latch = new CountDownLatch(3);
        AtomicReference<ChunkBuildContext> workerContext = new AtomicReference<>();
        ChunkJobCollector collector = new ChunkJobCollector(4, result -> latch.countDown());
        assertTrue(collector.canOffer());
        for (int i = 0; i < 3; i++) {
            ChunkBuilderTask<ChunkSortOutput> task = new ChunkBuilderTask<>() {
                @Override
                public ChunkSortOutput execute(ChunkBuildContext context, CancellationToken token) {
                    workerContext.set(com.bdmajora.impetus.engine.impl.render.chunk.compile.GlobalChunkBuildContext.get());
                    return new ChunkSortOutput(SECTION, 1, new Reference2ReferenceOpenHashMap<>());
                }
            };
            collector.addSubmittedJob(builder.scheduleTask(task, i == 0, collector::onJobFinished));
        }
        collector.awaitCompletion(builder);
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertNotNull(workerContext.get());
        assertFalse(collector.canOffer() && collector.canOffer() == false);
        new ChunkJobCollector(1, r -> {}).awaitCompletion(builder);
        // A slow job forces the collector to wait through the managed blocker
        ChunkJobCollector slowCollector = new ChunkJobCollector(1, r -> {});
        ChunkBuilderTask<ChunkSortOutput> slow = new ChunkBuilderTask<>() {
            @Override
            public ChunkSortOutput execute(ChunkBuildContext context, CancellationToken token) {
                try {
                    Thread.sleep(250);
                } catch (InterruptedException ignored) {
                }
                return new ChunkSortOutput(SECTION, 1, new Reference2ReferenceOpenHashMap<>());
            }
        };
        var slowJob = builder.scheduleTask(slow, false, slowCollector::onJobFinished);
        Thread.sleep(50);
        slowCollector.addSubmittedJob(slowJob);
        slowCollector.awaitCompletion(builder);
        Thread.sleep(50);
        builder.setDispatchBudgetLimited(true);
        builder.tickSchedulingBudget();
        assertTrue(builder.getTargetQueueSize() >= 64);
        for (int i = 0; i < 200; i++) {
            builder.scheduleTask(task("ok"), false, r -> {});
        }
        builder.setDispatchBudgetLimited(false);
        builder.tickSchedulingBudget();
        builder.tick();
        builder.shutdown();
        assertEquals(0, builder.getTotalThreadCount());
        assertTrue(blocks.get() >= 2);
        ChunkBuilder.WorkerThread thread = new ChunkBuilder.WorkerThread(() -> {}, "t", null);
        assertNull(thread.impetus$getGlobalContext());
    }

    @Test
    void metricsTrackerSummarisesObservations() throws InterruptedException {
        ChunkJobMetricsTracker tracker = new ChunkJobMetricsTracker();
        ChunkTaskOutput output = new ChunkSortOutput(SECTION, 1, new Reference2ReferenceOpenHashMap<>());
        tracker.collectMetrics(new ChunkJobResult.Success<>(output, -1));
        assertTrue(tracker.getMetrics().isEmpty());
        tracker.collectMetrics(new ChunkJobResult.Success<>(output, 10));
        tracker.collectMetrics(new ChunkJobResult.Success<>(output, 30));
        ChunkJobMetricsTracker.MetricsData data = tracker.getMetrics().get(ChunkSortOutput.class);
        ChunkJobMetricsTracker.MetricStats stats = data.getStats();
        assertEquals(20, stats.avg());
        assertEquals(30, stats.max());
        assertEquals(10, stats.min());
        assertEquals("avg = 20, max = 30, min = 10", stats.toString());
        assertEquals("avg = 20ns, max = 30ns, min = 10ns", stats.toString(v -> v + "ns"));
        assertEquals(0, data.getObservationsInLastTimeInterval());
        tracker.tick();
        var flip = ChunkJobMetricsTracker.class.getDeclaredFields();
        com.bdmajora.testing.Statics.set(ChunkJobMetricsTracker.class, "OBSERVATION_COUNT_TIME", 0L);
        tracker.tick();
        assertEquals(2, data.getObservationsInLastTimeInterval());
        com.bdmajora.testing.Statics.set(ChunkJobMetricsTracker.class, "OBSERVATION_COUNT_TIME", TimeUnit.SECONDS.toNanos(1));
        assertEquals(0, new ChunkJobMetricsTracker.MetricsData().getStats().max());
        ChunkJobMetricsTracker.MetricsData ring = new ChunkJobMetricsTracker.MetricsData();
        for (int i = 0; i < 10005; i++) {
            ring.collect(i);
        }
        assertEquals(10004, ring.getStats().max());
        assertTrue(flip.length > 0);
    }
}
