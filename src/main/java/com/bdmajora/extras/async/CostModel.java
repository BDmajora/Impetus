package com.bdmajora.extras.async;

import java.util.concurrent.atomic.LongAdder;

// Estimates the time of one work item to size parallel tasks.
public final class CostModel {
    // Time that one task should take.
    private static final long TARGET_TASK_NANOS = 250_000L;
    // Smallest total work that is worth running in parallel.
    private static final long MIN_PARALLEL_NANOS = 2L * TARGET_TASK_NANOS;
    // Weight of a new sample.
    private static final double SMOOTHING = 0.25D;

    // Time of the tasks since the last fold.
    private final LongAdder batchNanos = new LongAdder();
    // Items of the tasks since the last fold.
    private final LongAdder batchItems = new LongAdder();
    // Estimated time of one item.
    private volatile double nanosPerItem;

    // Creates a model that starts from a seed estimate limited to a sensible range.
    public CostModel(double seedNanosPerItem) {
        this.nanosPerItem = Double.isNaN(seedNanosPerItem) ? 100.0D : Math.min(2_000_000.0D, Math.max(100.0D, seedNanosPerItem));
    }

    // Returns the task wrapped to record its time.
    public Runnable wrap(int items, Runnable task) {
        return () -> {
            long start = System.nanoTime();
            try {
                task.run();
            } finally {
                this.batchNanos.add(System.nanoTime() - start);
                this.batchItems.add(items);
            }
        };
    }

    // Folds the recorded times into the estimate.
    private synchronized void fold() {
        long items = this.batchItems.sumThenReset();
        if (items <= 0L) {
            return;
        }
        long nanos = this.batchNanos.sumThenReset();
        double sample = Math.min(2_000_000.0D, Math.max(100.0D, (double) nanos / items));
        double current = this.nanosPerItem;
        this.nanosPerItem = current + (sample - current) * SMOOTHING;
    }

    // Returns the number of items for each task.
    public int chunkSize(int workItems, int workers) {
        fold();
        int byCost = (int) Math.min(Integer.MAX_VALUE, Math.max(1L, (long) (TARGET_TASK_NANOS / this.nanosPerItem)));
        int participants = Math.max(1, workers) + 1;
        int fairShare = Math.max(1, (workItems + participants - 1) / participants);
        return Math.min(byCost, Math.max(1, fairShare));
    }

    // Returns whether the work is too small for parallel tasks.
    public boolean shouldRunSequentially(int workItems) {
        fold();
        return workItems * this.nanosPerItem < MIN_PARALLEL_NANOS;
    }
}
