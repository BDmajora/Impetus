package com.bdmajora.impetus.engine.impl.render.chunk.compile.executor;

import com.bdmajora.impetus.engine.impl.render.chunk.RenderSection;
import com.bdmajora.impetus.engine.impl.render.chunk.compile.ChunkBuildBuffersTest;
import com.bdmajora.impetus.engine.impl.render.chunk.compile.ChunkBuildContext;
import com.bdmajora.impetus.engine.impl.render.chunk.compile.ChunkBuildOutput;
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
        // A job cancelled mid-run or before it starts still reports, with no result, so a waiting collector wakes
        new ChunkJobTyped<>(task("null"), results::add).execute(context());
        assertEquals(3, results.size());
        assertNull(results.get(2));
        ChunkJobTyped<ChunkBuilderTask<ChunkSortOutput>, ChunkSortOutput> cancelled = new ChunkJobTyped<>(task("ok"), results::add);
        cancelled.setCancelled();
        assertTrue(cancelled.isCancelled());
        cancelled.execute(context());
        assertFalse(cancelled.isStarted());
        assertEquals(4, results.size());
        assertNull(results.get(3));
        ChunkJobTyped<ChunkBuilderTask<ChunkSortOutput>, ChunkSortOutput> badConsumer = new ChunkJobTyped<>(task("ok"), r -> {
            throw new IllegalArgumentException("consumer");
        });
        assertThrows(RuntimeException.class, () -> badConsumer.execute(context()));
    }

    @Test
    void builderWithoutWorkersRunsJobsOnTick() {
        ChunkBuilder builder = new ChunkBuilder(ChunkBuilder.ManagedBlocker.NONE, ExecutorTest::context, -1);
        assertEquals(0, builder.getTotalThreadCount());
        // With no workers the in-flight target is the floor of one worker's worth
        assertEquals(2, builder.getTargetQueueSize());
        assertEquals(2, builder.getSchedulingBudget());
        assertEquals(8, builder.getSortSchedulingBudget());
        List<ChunkJobResult<ChunkSortOutput>> results = new ArrayList<>();
        var job = builder.scheduleTask(task("ok"), true, 50, results::add);
        builder.scheduleTask(task("ok"), false, 10, results::add);
        assertEquals(2, builder.getScheduledJobCount());
        assertFalse(builder.isBuildQueueEmpty());
        assertEquals(0, builder.getSchedulingBudget());
        // Sorts are never starved completely, even with the mesh budget spent
        assertEquals(4, builder.getSortSchedulingBudget());
        builder.tryStealTask(job);
        assertEquals(1, results.size());
        builder.tryStealTask(job);
        builder.tick();
        assertEquals(2, results.size());
        assertTrue(builder.isBuildQueueEmpty());
        ChunkJobMetricsTracker metrics = new ChunkJobMetricsTracker();
        builder.tickSchedulingBudget(metrics);
        builder.tickSchedulingBudget(metrics);
        assertEquals(2, builder.getTargetQueueSize());
        assertEquals(0, builder.getBusyThreadCount());
        assertThrows(NullPointerException.class, () -> builder.scheduleTask(null, false, 0, r -> {}));
        builder.managedBlock(() -> true);
        builder.shutdown();
        assertThrows(IllegalStateException.class, builder::shutdown);
        assertThrows(IllegalStateException.class, () -> builder.scheduleTask(task("ok"), false, 0, r -> {}));
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
        assertEquals(4, builder.getTargetQueueSize());
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
            collector.addSubmittedJob(builder.scheduleTask(task, i == 0, i, collector::onJobFinished));
        }
        collector.awaitCompletion(builder);
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertNotNull(workerContext.get());
        assertTrue(collector.canOffer());
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
        var slowJob = builder.scheduleTask(slow, false, 0, slowCollector::onJobFinished);
        Thread.sleep(50);
        slowCollector.addSubmittedJob(slowJob);
        slowCollector.awaitCompletion(builder);
        Thread.sleep(50);
        builder.tickSchedulingBudget(new ChunkJobMetricsTracker());
        assertEquals(4, builder.getTargetQueueSize());
        // Whatever is still queued at shutdown is cancelled rather than run
        for (int i = 0; i < 200; i++) {
            builder.scheduleTask(task("ok"), false, i, r -> {});
        }
        builder.tick();
        builder.shutdown();
        assertEquals(0, builder.getTotalThreadCount());
        assertTrue(blocks.get() >= 2);
        ChunkBuilder.WorkerThread thread = new ChunkBuilder.WorkerThread(() -> {}, "t", null);
        assertNull(thread.impetus$getGlobalContext());
    }

    @Test
    void metricsTrackerSummarisesObservations() {
        ChunkJobMetricsTracker tracker = new ChunkJobMetricsTracker();
        ChunkTaskOutput output = new ChunkSortOutput(SECTION, 1, new Reference2ReferenceOpenHashMap<>());
        // A synthesised result (no task ran) carries no timing
        tracker.collectMetrics(new ChunkJobResult.Success<>(output, -1));
        assertTrue(tracker.getMetrics().isEmpty());
        assertEquals(99, tracker.getAverageExecutionNanos(ChunkSortOutput.class, 99));
        tracker.collectMetrics(new ChunkJobResult.Success<>(output, 10));
        tracker.collectMetrics(new ChunkJobResult.Success<>(output, 30));
        ChunkJobMetricsTracker.MetricsData data = tracker.getMetrics().get(ChunkSortOutput.class);
        ChunkJobMetricsTracker.MetricStats stats = data.getStats();
        assertEquals(20, stats.avg());
        assertEquals(30, stats.max());
        assertEquals(10, stats.min());
        assertEquals("avg = 20, max = 30, min = 10", stats.toString());
        assertEquals("avg = 20ns, max = 30ns, min = 10ns", stats.toString(v -> v + "ns"));
        // The moving average starts at the first sample and moves a twentieth of the way to each later one
        assertEquals(11, tracker.getAverageExecutionNanos(ChunkSortOutput.class, 99), 1e-9);
        assertEquals(99, tracker.getAverageExecutionNanos(ChunkBuildOutput.class, 99));
        assertEquals(0, data.getObservationsInLastTimeInterval());
        tracker.tick();
        com.bdmajora.testing.Statics.set(ChunkJobMetricsTracker.class, "OBSERVATION_COUNT_TIME", 0L);
        try {
            tracker.tick();
        } finally {
            com.bdmajora.testing.Statics.set(ChunkJobMetricsTracker.class, "OBSERVATION_COUNT_TIME", TimeUnit.SECONDS.toNanos(1));
        }
        assertEquals(2, data.getObservationsInLastTimeInterval());
        ChunkJobMetricsTracker.MetricsData fresh = new ChunkJobMetricsTracker.MetricsData();
        assertEquals(0, fresh.getStats().max());
        assertEquals(5, fresh.getAverageNanos(5));
        ChunkJobMetricsTracker.MetricsData ring = new ChunkJobMetricsTracker.MetricsData();
        for (int i = 0; i < 10005; i++) {
            ring.collect(i);
        }
        assertEquals(10004, ring.getStats().max());
        assertEquals(5, ring.getStats().min());
    }

    @Test
    void failuresRethrowWhatTheJobThrew() {
        IllegalStateException crash = new IllegalStateException("crash");
        assertSame(crash, assertThrows(IllegalStateException.class, () -> new ChunkJobResult.Failure<>(crash).abort()));
        Exception checked = new java.io.IOException("io");
        assertSame(checked, assertThrows(RuntimeException.class, () -> new ChunkJobResult.Failure<>(checked).abort()).getCause());
        assertThrows(NullPointerException.class, () -> new ChunkJobResult.Failure<>(null));
    }
}
