package com.bdmajora.extras.async;

import java.util.concurrent.atomic.LongAdder;

// Learns how long one work item takes so batches can be cut to about a quarter millisecond each: too fine and the queue overhead dominates, too coarse and one slow section leaves the other cores idle
public final class CostModel {
    private static final long TARGET_TASK_NANOS = 250_000L;
    private static final long MIN_PARALLEL_NANOS = 2L * TARGET_TASK_NANOS;
    private static final double SMOOTHING = 0.25D;

    private final LongAdder batchNanos = new LongAdder();
    private final LongAdder batchItems = new LongAdder();
    private volatile double nanosPerItem;

    public CostModel(double seedNanosPerItem) {
        this.nanosPerItem = seedNanosPerItem;
    }

    // Times a batch and records its item count for the next fold
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

    // Folds the last round of samples into the running estimate, clamped so one pathological batch cannot swing it
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

    // Items per task: enough to fill the target, but never more than an even share so every worker gets something
    public int chunkSize(int workItems, int workers) {
        fold();
        int byCost = (int) Math.max(1L, (long) (TARGET_TASK_NANOS / this.nanosPerItem));
        int participants = Math.max(1, workers) + 1;
        int fairShare = Math.max(1, (workItems + participants - 1) / participants);
        return Math.min(byCost, Math.max(1, fairShare));
    }

    // Below two tasks' worth of work the hand-off costs more than it saves
    public boolean shouldRunSequentially(int workItems) {
        fold();
        return workItems * this.nanosPerItem < MIN_PARALLEL_NANOS;
    }
}
