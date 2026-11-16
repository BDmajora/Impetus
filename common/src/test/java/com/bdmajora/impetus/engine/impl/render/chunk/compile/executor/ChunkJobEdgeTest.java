package com.bdmajora.impetus.engine.impl.render.chunk.compile.executor;

import com.bdmajora.impetus.engine.impl.render.chunk.compile.ChunkBuildContext;
import com.bdmajora.impetus.engine.impl.render.chunk.compile.ChunkTaskOutput;
import com.bdmajora.impetus.engine.impl.render.chunk.compile.GlobalChunkBuildContext;
import com.bdmajora.impetus.engine.impl.render.chunk.compile.estimation.UploadDurationEstimator;
import com.bdmajora.impetus.engine.impl.render.chunk.compile.tasks.ChunkBuilderTask;
import com.bdmajora.impetus.engine.impl.util.task.CancellationToken;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// Tests job execution and result handling and the estimator.
class ChunkJobEdgeTest {
    // Task that returns a fixed output or throws.
    private static ChunkBuilderTask<String> task(String output, RuntimeException failure) {
        return new ChunkBuilderTask<>() {
            @Override
            public String execute(ChunkBuildContext context, CancellationToken cancellationToken) {
                if (failure != null) {
                    throw failure;
                }
                return output;
            }
        };
    }

    // Reports a success with its run time.
    @Test
    void successIsReported() {
        List<ChunkJobResult<String>> seen = new ArrayList<>();
        ChunkJobTyped<ChunkBuilderTask<String>, String> job = new ChunkJobTyped<>(task("x", null), seen::add);
        assertFalse(job.isStarted());
        job.execute(null);
        assertTrue(job.isStarted());
        assertEquals(1, seen.size());
        ChunkJobResult.Success<String> success = assertInstanceOf(ChunkJobResult.Success.class, seen.get(0));
        assertEquals("x", success.output());
        assertTrue(success.executionTimeNanos() >= 0);
    }

    // Reports a failure for a throwing task and never throws from execute.
    @Test
    void failureIsReported() {
        List<ChunkJobResult<String>> seen = new ArrayList<>();
        IllegalStateException boom = new IllegalStateException("boom");
        new ChunkJobTyped<>(task(null, boom), seen::add).execute(null);
        ChunkJobResult.Failure<String> failure = assertInstanceOf(ChunkJobResult.Failure.class, seen.get(0));
        assertSame(boom, failure.throwable());
        assertSame(boom, assertThrows(IllegalStateException.class, failure::abort));
        Exception checked = new Exception("checked");
        RuntimeException wrapped = assertThrows(RuntimeException.class, new ChunkJobResult.Failure<String>(checked)::abort);
        assertSame(checked, wrapped.getCause());
        assertThrows(NullPointerException.class, () -> new ChunkJobResult.Failure<String>(null));
    }

    // Reports null for a cancelled job and for a task that answers null.
    @Test
    void cancelledAndNullOutputsReportNull() {
        List<ChunkJobResult<String>> seen = new ArrayList<>();
        ChunkJobTyped<ChunkBuilderTask<String>, String> cancelled = new ChunkJobTyped<>(task("x", null), seen::add);
        assertFalse(cancelled.isCancelled());
        cancelled.setCancelled();
        assertTrue(cancelled.isCancelled());
        cancelled.execute(null);
        assertFalse(cancelled.isStarted());
        new ChunkJobTyped<>(task(null, null), seen::add).execute(null);
        assertEquals(2, seen.size());
        assertNull(seen.get(0));
        assertNull(seen.get(1));
    }

    // Wraps a failing receiver and rejects null parts.
    @Test
    void receiverFailureAndNulls() {
        assertThrows(RuntimeException.class, () -> new ChunkJobTyped<>(task("x", null), r -> {
            throw new IllegalStateException("consumer");
        }).execute(null));
        assertThrows(NullPointerException.class, () -> new ChunkJobTyped<ChunkBuilderTask<String>, String>(null, r -> {}));
        assertThrows(NullPointerException.class, () -> new ChunkJobTyped<>(task("x", null), null));
    }

    // Counts a result unless it is null and respects the budget.
    @Test
    void collectorCountsAndBudgets() {
        List<Object> seen = new ArrayList<>();
        ChunkJobCollector collector = new ChunkJobCollector(1, seen::add);
        assertTrue(collector.canOffer());
        ChunkJob job = Mockito.mock(ChunkJob.class);
        collector.addSubmittedJob(job);
        assertFalse(collector.canOffer());
        collector.onJobFinished(null);
        assertTrue(seen.isEmpty());
        assertThrows(NullPointerException.class, () -> collector.addSubmittedJob(null));
        assertThrows(NullPointerException.class, () -> new ChunkJobCollector(1, null));
        assertFalse(new ChunkJobCollector(0, seen::add).canOffer());
        assertFalse(new ChunkJobCollector(-5, seen::add).canOffer());
        assertFalse(new ChunkJobCollector(Integer.MIN_VALUE, seen::add).canOffer());
        new ChunkJobCollector(3, seen::add).awaitCompletion(null);
    }

    // Waits for every job and stays reusable.
    @Test
    void awaitClearsSubmittedJobs() {
        ChunkBuilder builder = Mockito.mock(ChunkBuilder.class);
        ChunkJobCollector collector = new ChunkJobCollector(2, r -> {});
        ChunkJob job = Mockito.mock(ChunkJob.class);
        Mockito.when(job.isStarted()).thenReturn(true);
        collector.addSubmittedJob(job);
        collector.onJobFinished(null);
        collector.awaitCompletion(builder);
        assertTrue(collector.canOffer());
        collector.awaitCompletion(builder);
        assertThrows(NullPointerException.class, () -> {
            collector.addSubmittedJob(job);
            collector.awaitCompletion(null);
        });
    }

    // Starts at the default rate and averages new samples.
    @Test
    void estimatorBehaviour() {
        UploadDurationEstimator estimator = new UploadDurationEstimator();
        assertEquals(0, estimator.estimateUploadDuration(0));
        assertEquals(0, estimator.estimateUploadDuration(-5));
        assertEquals(1, estimator.estimateUploadDuration(1));
        assertEquals(250, estimator.estimateUploadDuration(1000));
        estimator.recordUpload(0, 10);
        estimator.recordUpload(10, 0);
        estimator.recordUpload(-1, 10);
        estimator.recordUpload(10, -1);
        assertEquals(0, estimator.getLastUploadBytes());
        estimator.recordUpload(1000, 2000);
        assertEquals(1000, estimator.getLastUploadBytes());
        assertEquals(2000, estimator.getLastUploadDurationNanos());
        assertEquals(250, estimator.getLastUploadEstimateNanos());
        assertEquals(2000, estimator.estimateUploadDuration(1000));
        estimator.recordUpload(1000, 4000);
        assertEquals(2100, estimator.estimateUploadDuration(1000));
    }

    // Saturates the estimate for the largest sizes.
    @Test
    void estimatorSaturates() {
        UploadDurationEstimator estimator = new UploadDurationEstimator();
        assertTrue(estimator.estimateUploadDuration(Long.MAX_VALUE) > 0);
        estimator.recordUpload(1, Long.MAX_VALUE);
        assertEquals(Long.MAX_VALUE, estimator.estimateUploadDuration(Long.MAX_VALUE));
        estimator.recordUpload(Long.MAX_VALUE, 1);
        assertTrue(estimator.estimateUploadDuration(1) >= 1);
    }

    // Returns the bound context only on the main thread.
    @Test
    void globalContextIsPerThread() throws InterruptedException {
        ChunkBuildContext context = Mockito.mock(ChunkBuildContext.class);
        GlobalChunkBuildContext.setMainThread();
        GlobalChunkBuildContext.bindMainThread(context);
        assertSame(context, GlobalChunkBuildContext.get());
        ChunkBuildContext[] other = new ChunkBuildContext[1];
        Thread thread = new Thread(() -> other[0] = GlobalChunkBuildContext.get());
        thread.start();
        thread.join();
        assertNull(other[0]);
        GlobalChunkBuildContext.bindMainThread(null);
        assertNull(GlobalChunkBuildContext.get());
    }
}
