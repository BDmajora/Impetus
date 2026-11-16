package com.bdmajora.impetus.engine.impl.render.chunk.compile.executor;

import org.jetbrains.annotations.Nullable;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Comparator;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.concurrent.Semaphore;

// Priority queue of chunk jobs shared by the workers and the main thread.
class ChunkJobQueue {
    // Priority of important jobs that run before every other job.
    static final long IMPORTANT_PRIORITY = Long.MIN_VALUE;

    // Order by priority and then by arrival.
    @SuppressWarnings("ComparatorCombinators")
    private static final Comparator<ChunkJobTyped<?, ?>> ORDER = (a, b) -> {
        int result = Long.compare(a.priority, b.priority);
        return result != 0 ? result : Long.compare(a.sequence, b.sequence);
    };

    // Waiting jobs guarded by its own lock.
    private final PriorityQueue<ChunkJobTyped<?, ?>> jobs = new PriorityQueue<>(ORDER);
    // Arrival number of the next job.
    private long nextSequence;

    // Counts the waiting jobs.
    private final Semaphore semaphore = new Semaphore(0);

    // Whether the queue accepts and hands out jobs.
    private volatile boolean running = true;

    // Returns whether the queue is running.
    public boolean isRunning() {
        return this.running;
    }

    // Adds a job with a priority and rejects a job after shutdown.
    public void add(ChunkJobTyped<?, ?> job, long priority) {
        Objects.requireNonNull(job, "job");

        synchronized (this.jobs) {
            if (!this.running) {
                throw new IllegalStateException("Queue is no longer running");
            }

            job.priority = priority;
            job.sequence = this.nextSequence++;
            this.jobs.add(job);
        }

        this.semaphore.release(1);
    }

    // Returns the next job or null without waiting.
    @Nullable
    public ChunkJob pollJob() {
        return this.isRunning() && this.semaphore.tryAcquire() ? this.getNextTask() : null;
    }

    // Waits for the next job and returns null after shutdown.
    @Nullable
    public ChunkJob waitForNextJob() throws InterruptedException {
        if (!this.isRunning()) {
            return null;
        }

        this.semaphore.acquire();

        return this.getNextTask();
    }

    // Removes a waiting job and returns whether it was still waiting.
    public boolean stealJob(ChunkJob job) {
        if (!this.semaphore.tryAcquire()) {
            return false;
        }

        boolean success;

        synchronized (this.jobs) {
            success = this.jobs.remove(job);
        }

        if (!success) {
            this.semaphore.release(1);
        }

        return success;
    }

    // Removes and returns the first waiting job or null.
    @Nullable
    private ChunkJob getNextTask() {
        synchronized (this.jobs) {
            return this.jobs.poll();
        }
    }

    // Stops the queue and returns the jobs that never ran and wakes every waiting worker.
    public Collection<ChunkJob> shutdown() {
        var list = new ArrayDeque<ChunkJob>();

        synchronized (this.jobs) {
            this.running = false;

            ChunkJobTyped<?, ?> job;
            while ((job = this.jobs.poll()) != null) {
                list.add(job);
            }
        }

        this.semaphore.drainPermits();
        this.semaphore.release(WAKE_PERMITS);

        return list;
    }

    // Returns the number of jobs that no worker claimed.
    public int size() {
        return this.running ? this.semaphore.availablePermits() : 0;
    }

    // Returns whether no unclaimed job remains.
    public boolean isEmpty() {
        return this.size() == 0;
    }

    // Permits released at shutdown so every blocked worker wakes.
    private static final int WAKE_PERMITS = 1 << 20;
}
