package com.bdmajora.impetus.engine.impl.render.chunk.compile.executor;

import org.jetbrains.annotations.Nullable;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

class ChunkJobQueue {
    private final ConcurrentLinkedDeque<ChunkJob> jobs = new ConcurrentLinkedDeque<>();

    private final Semaphore semaphore = new Semaphore(0);

    private final AtomicBoolean isRunning = new AtomicBoolean(true);

    // Set by a worker whenever it blocks on an empty queue; read and cleared once per frame by the scheduler to detect under-provisioning
    private final AtomicBoolean workerBlocked = new AtomicBoolean(false);

    // False after shutdown
    public boolean isRunning() {
        return this.isRunning.get();
    }

    // Important jobs go to the front
    public void add(ChunkJob job, boolean important) {
        if (!this.isRunning()) {
            throw new IllegalStateException("Queue is no longer running");
        }

        if (important) {
            this.jobs.addFirst(job);
        } else {
            this.jobs.addLast(job);
        }

        this.semaphore.release(1);
    }

    // Next job or null, without blocking
    @Nullable
    public ChunkJob pollJob() {
        return this.isRunning() && this.semaphore.tryAcquire() ? this.jobs.poll() : null;
    }

    // Blocks until a job arrives or shutdown
    @Nullable
    public ChunkJob waitForNextJob() throws InterruptedException {
        if (!this.isRunning()) {
            return null;
        }

        if (!this.semaphore.tryAcquire()) {
            // No work available and about to block, so record it so the scheduler can grow the in-flight target
            this.workerBlocked.set(true);
            this.semaphore.acquire();
        }

        // Important jobs sit at the front
        return this.jobs.poll();
    }

    // Whether any worker blocked on an empty queue since the last call, read-and-cleared atomically so two windows never count the same block; the scheduler's starvation signal
    public boolean checkAndClearWorkerBlocked() {
        return this.workerBlocked.getAndSet(false);
    }

    // Removes a specific job if still queued, so a thief can run it
    public boolean stealJob(ChunkJob job) {
        if (!this.semaphore.tryAcquire()) {
            return false;
        }

        var success = this.jobs.remove(job);

        if (!success) {
            // If we didn't manage to actually steal the task, then we need to release the permit which we did steal
            this.semaphore.release(1);
        }

        return success;
    }

    // Stops accepting and returns whatever was still queued
    public Collection<ChunkJob> shutdown() {
        var list = new ArrayDeque<ChunkJob>();

        this.isRunning.set(false);

        while (this.semaphore.tryAcquire()) {
            var task = this.jobs.poll();

            if (task != null) {
                list.add(task);
            }
        }

        // force the worker threads to wake up and exit
        this.semaphore.release(Runtime.getRuntime().availableProcessors());

        return list;
    }

    // Both queues
    public int size() {
        return this.semaphore.availablePermits();
    }

    // Both queues
    public boolean isEmpty() {
        return this.size() == 0;
    }
}
