package com.bdmajora.impetus.engine.impl.render.chunk.compile.executor;

import com.bdmajora.impetus.engine.impl.render.chunk.compile.ChunkTaskOutput;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

// Collects the results of jobs submitted within a frame budget.
public class ChunkJobCollector {
    // Counts finished jobs.
    private final Semaphore semaphore = new Semaphore(0);
    // Receiver of the results.
    private final Consumer<ChunkJobResult<? extends ChunkTaskOutput>> collector;
    // Jobs submitted and not yet awaited.
    private final List<ChunkJob> submitted = new ArrayList<>();

    // Number of jobs that may be submitted.
    private final int budget;

    // Creates a collector and rejects a null receiver.
    public ChunkJobCollector(int budget, Consumer<ChunkJobResult<? extends ChunkTaskOutput>> collector) {
        this.budget = budget;
        this.collector = java.util.Objects.requireNonNull(collector, "collector");
    }

    // Counts a finished job and passes its result on unless it was cancelled.
    public void onJobFinished(@Nullable ChunkJobResult<? extends ChunkTaskOutput> result) {
        this.semaphore.release(1);
        if (result != null) {
            this.collector.accept(result);
        }
    }

    // Waits until every submitted job finished and clears the submitted jobs.
    public void awaitCompletion(ChunkBuilder builder) {
        if (this.submitted.isEmpty()) {
            return;
        }

        java.util.Objects.requireNonNull(builder, "builder");

        // Steals the jobs no worker started
        for (var job : this.submitted) {
            if (job.isStarted()) {
                continue;
            }

            builder.tryStealTask(job);
        }

        // Takes one permit per submitted job
        int remaining = this.submitted.size();
        BooleanSupplier isDone = () -> this.semaphore.availablePermits() > 0;

        while (remaining > 0) {
            int avail = this.semaphore.availablePermits();
            if (avail > 0) {
                int toTake = Math.min(avail, remaining);
                if (this.semaphore.tryAcquire(toTake)) {
                    remaining -= toTake;
                    continue;
                }
            }

            // Runs a task while waiting
            builder.managedBlock(isDone);
        }

        this.submitted.clear();
    }

    // Adds a job to wait for and rejects null.
    public void addSubmittedJob(ChunkJob job) {
        this.submitted.add(java.util.Objects.requireNonNull(job, "job"));
    }

    // Returns whether the budget allows another job.
    public boolean canOffer() {
        return (this.budget - this.submitted.size()) > 0;
    }
}
