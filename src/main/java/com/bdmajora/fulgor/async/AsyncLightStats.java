package com.bdmajora.fulgor.async;

import java.util.concurrent.atomic.LongAdder;

// Counters of the async light engine.
public final class AsyncLightStats {
    // Number of chunks queued.
    public static final LongAdder CHUNKS_QUEUED = new LongAdder();
    // Number of block changes.
    public static final LongAdder BLOCK_CHANGES = new LongAdder();
    // Number of sky tasks run.
    public static final LongAdder SKY_TASKS = new LongAdder();
    // Number of block tasks run.
    public static final LongAdder BLOCK_TASKS = new LongAdder();
    // Number of initial lightings.
    public static final LongAdder INITIAL_LIGHTS = new LongAdder();
    // Number of positions processed.
    public static final LongAdder POSITIONS_PROCESSED = new LongAdder();
    // Time spent by the sky worker.
    public static final LongAdder SKY_WORKER_NANOS = new LongAdder();
    // Time spent by the block worker.
    public static final LongAdder BLOCK_WORKER_NANOS = new LongAdder();
    // Number of queue overflows.
    public static final LongAdder QUEUE_OVERFLOWS = new LongAdder();

    // Prevents instantiation
    private AsyncLightStats() {
    }
}
