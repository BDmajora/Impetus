package com.bdmajora.impetus.engine.impl.render.chunk.compile.executor;

import com.bdmajora.impetus.engine.impl.render.chunk.compile.tasks.ChunkBuilderTask;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

// Tests ordering and shutdown of the job queue.
class ChunkJobQueueTest {
    // Creates a job.
    private static ChunkJobTyped<ChunkBuilderTask<String>, String> job() {
        return new ChunkJobTyped<>(new ChunkBuilderTask<>() {
            @Override
            public String execute(com.bdmajora.impetus.engine.impl.render.chunk.compile.ChunkBuildContext context, com.bdmajora.impetus.engine.impl.util.task.CancellationToken token) {
                return "";
            }
        }, r -> {});
    }

    // Hands out important jobs first and equal priorities in arrival order.
    @Test
    void ordering() {
        ChunkJobQueue queue = new ChunkJobQueue();
        var a = job();
        var b = job();
        var c = job();
        var d = job();
        queue.add(a, 5);
        queue.add(b, 5);
        queue.add(c, ChunkJobQueue.IMPORTANT_PRIORITY);
        queue.add(d, Long.MAX_VALUE);
        assertEquals(4, queue.size());
        assertSame(c, queue.pollJob());
        assertSame(a, queue.pollJob());
        assertSame(b, queue.pollJob());
        assertSame(d, queue.pollJob());
        assertNull(queue.pollJob());
        assertTrue(queue.isEmpty());
    }

    // Steals a waiting job once and refuses jobs that are not waiting.
    @Test
    void stealing() {
        ChunkJobQueue queue = new ChunkJobQueue();
        var a = job();
        var b = job();
        queue.add(a, 1);
        assertFalse(queue.stealJob(b));
        assertEquals(1, queue.size());
        assertTrue(queue.stealJob(a));
        assertFalse(queue.stealJob(a));
        assertFalse(queue.stealJob(null));
        assertTrue(queue.isEmpty());
        assertThrows(NullPointerException.class, () -> queue.add(null, 0));
    }

    // Returns the unclaimed jobs at shutdown and rejects later work.
    @Test
    void shutdownReturnsJobs() {
        ChunkJobQueue queue = new ChunkJobQueue();
        var a = job();
        var b = job();
        queue.add(a, 2);
        queue.add(b, 1);
        Collection<ChunkJob> left = queue.shutdown();
        assertEquals(List.of(b, a), new ArrayList<>(left));
        assertFalse(queue.isRunning());
        assertThrows(IllegalStateException.class, () -> queue.add(job(), 0));
        assertNull(queue.pollJob());
        assertEquals(0, queue.size());
        assertTrue(queue.shutdown().isEmpty());
    }

    // Wakes every blocked worker even when there are more workers than processors.
    @Test
    void shutdownWakesAllWorkers() throws InterruptedException {
        ChunkJobQueue queue = new ChunkJobQueue();
        int workers = Runtime.getRuntime().availableProcessors() * 3 + 2;
        AtomicInteger woke = new AtomicInteger();
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < workers; i++) {
            Thread thread = new Thread(() -> {
                try {
                    assertNull(queue.waitForNextJob());
                    woke.incrementAndGet();
                } catch (InterruptedException ignored) {
                }
            });
            thread.setDaemon(true);
            thread.start();
            threads.add(thread);
        }
        Thread.sleep(100);
        queue.shutdown();
        for (Thread thread : threads) {
            thread.join(5000);
        }
        assertEquals(workers, woke.get());
        assertNull(queue.waitForNextJob());
    }

    // Never loses a job that races with shutdown.
    @Test
    void addRacingShutdownNeverStrandsAJob() throws InterruptedException {
        for (int round = 0; round < 200; round++) {
            ChunkJobQueue queue = new ChunkJobQueue();
            AtomicInteger accepted = new AtomicInteger();
            Thread adder = new Thread(() -> {
                for (int i = 0; i < 50; i++) {
                    try {
                        queue.add(job(), i);
                        accepted.incrementAndGet();
                    } catch (IllegalStateException e) {
                        return;
                    }
                }
            });
            adder.start();
            Collection<ChunkJob> left = queue.shutdown();
            adder.join();
            assertEquals(accepted.get(), left.size(), "round " + round);
        }
    }
}
