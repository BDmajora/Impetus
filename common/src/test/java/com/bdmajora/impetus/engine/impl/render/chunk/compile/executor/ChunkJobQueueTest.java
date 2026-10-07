package com.bdmajora.impetus.engine.impl.render.chunk.compile.executor;

import com.bdmajora.impetus.engine.impl.render.chunk.compile.ChunkBuildContext;
import com.bdmajora.impetus.engine.impl.render.chunk.compile.tasks.ChunkBuilderTask;
import com.bdmajora.impetus.engine.impl.util.task.CancellationToken;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class ChunkJobQueueTest {
    private static ChunkJobTyped<ChunkBuilderTask<Void>, Void> job() {
        return new ChunkJobTyped<>(new ChunkBuilderTask<>() {
            @Override
            public Void execute(ChunkBuildContext context, CancellationToken token) {
                return null;
            }
        }, result -> {});
    }

    @Test
    void ordersByImportanceDistanceAndSubmissionOrder() throws InterruptedException {
        ChunkJobQueue queue = new ChunkJobQueue();
        var far = job();
        var nearFirst = job();
        var nearSecond = job();
        var importantFirst = job();
        var importantSecond = job();
        queue.add(far, 100);
        queue.add(nearFirst, 10);
        queue.add(importantFirst, ChunkJobQueue.IMPORTANT_PRIORITY);
        queue.add(nearSecond, 10);
        queue.add(importantSecond, ChunkJobQueue.IMPORTANT_PRIORITY);

        assertFalse(queue.stealJob(job()));
        assertEquals(5, queue.size());
        assertSame(importantFirst, queue.waitForNextJob());
        assertSame(importantSecond, queue.pollJob());
        assertSame(nearFirst, queue.pollJob());
        assertTrue(queue.stealJob(far));
        assertSame(nearSecond, queue.waitForNextJob());
        assertTrue(queue.isEmpty());
        assertNull(queue.pollJob());
        assertFalse(queue.stealJob(far));
        assertTrue(queue.shutdown().isEmpty());
    }

    @Test
    void concurrentSubmissionPollingAndStealingDeliverEveryJobExactlyOnce() throws Exception {
        int producerCount = 4;
        int workerCount = Math.min(24, Runtime.getRuntime().availableProcessors());
        int jobCount = 32_000;
        ChunkJobQueue queue = new ChunkJobQueue();
        List<ChunkJobTyped<ChunkBuilderTask<Void>, Void>> jobs = new ArrayList<>(jobCount);
        for (int i = 0; i < jobCount; i++) {
            jobs.add(job());
        }

        Set<ChunkJob> received = ConcurrentHashMap.newKeySet();
        CountDownLatch delivered = new CountDownLatch(jobCount);
        CountDownLatch ready = new CountDownLatch(producerCount + workerCount + 2);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(producerCount + workerCount + 2);
        List<Future<?>> producers = new ArrayList<>();
        List<Future<?>> consumers = new ArrayList<>();
        try {
            for (int producer = 0; producer < producerCount; producer++) {
                int first = producer;
                producers.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    for (int i = first; i < jobCount; i += producerCount) {
                        queue.add(jobs.get(i), i % 128 == 0 ? ChunkJobQueue.IMPORTANT_PRIORITY : i % 1024);
                    }
                    return null;
                }));
            }
            for (int worker = 0; worker < workerCount; worker++) {
                consumers.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    while (queue.isRunning()) {
                        ChunkJob next = queue.waitForNextJob();
                        if (next != null) {
                            recordDelivery(next, received, delivered);
                        }
                    }
                    return null;
                }));
            }
            consumers.add(executor.submit(() -> {
                ready.countDown();
                start.await();
                while (queue.isRunning()) {
                    ChunkJob next = queue.pollJob();
                    if (next != null) {
                        recordDelivery(next, received, delivered);
                    } else {
                        Thread.yield();
                    }
                }
                return null;
            }));
            consumers.add(executor.submit(() -> {
                ready.countDown();
                start.await();
                int index = 0;
                while (queue.isRunning()) {
                    ChunkJob target = jobs.get(index);
                    if (queue.stealJob(target)) {
                        recordDelivery(target, received, delivered);
                    }
                    index = (index + 1) % jobCount;
                    Thread.yield();
                }
                return null;
            }));

            assertTrue(ready.await(5, TimeUnit.SECONDS), "All producers and consumers must start");
            start.countDown();
            for (Future<?> producer : producers) {
                producer.get(10, TimeUnit.SECONDS);
            }
            boolean complete = delivered.await(10, TimeUnit.SECONDS);
            for (Future<?> consumer : consumers) {
                if (consumer.isDone()) {
                    consumer.get();
                }
            }
            assertTrue(complete, "No jobs may be lost during concurrent queue operations");
            assertEquals(Set.copyOf(jobs), received);
            assertTrue(queue.isEmpty());
            assertTrue(queue.shutdown().isEmpty());
            for (Future<?> consumer : consumers) {
                consumer.get(5, TimeUnit.SECONDS);
            }
        } finally {
            try {
                if (queue.isRunning()) {
                    queue.shutdown();
                }
            } finally {
                start.countDown();
                executor.shutdownNow();
                assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS), "Queue workers must terminate");
            }
        }
    }

    private static void recordDelivery(ChunkJob job, Set<ChunkJob> received, CountDownLatch delivered) {
        assertTrue(received.add(job), "A job must only be delivered once");
        delivered.countDown();
    }

    @Test
    void shutdownDrainsQueuedJobsAndWakesWaitingWorkers() throws Exception {
        ChunkJobQueue queued = new ChunkJobQueue();
        var first = job();
        var second = job();
        queued.add(first, 10);
        queued.add(second, 20);
        assertEquals(List.of(first, second), new ArrayList<>(queued.shutdown()));
        assertFalse(queued.isRunning());
        assertNull(queued.pollJob());
        assertNull(queued.waitForNextJob());
        assertThrows(IllegalStateException.class, () -> queued.add(job(), 0));

        ChunkJobQueue empty = new ChunkJobQueue();
        CountDownLatch waiting = new CountDownLatch(1);
        try (var executor = Executors.newSingleThreadExecutor()) {
            Future<ChunkJob> worker = executor.submit(() -> {
                waiting.countDown();
                return empty.waitForNextJob();
            });
            try {
                assertTrue(waiting.await(5, TimeUnit.SECONDS));
                assertTrue(empty.shutdown().isEmpty());
                assertNull(worker.get(5, TimeUnit.SECONDS));
            } finally {
                executor.shutdownNow();
            }
        }
    }
}
