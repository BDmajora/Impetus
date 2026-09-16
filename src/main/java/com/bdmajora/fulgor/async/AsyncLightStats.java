package com.bdmajora.fulgor.async;

import java.util.concurrent.atomic.LongAdder;

// Process-wide counters for the async engine, read by /fulgor and the F3 line; LongAdders since the client, the integrated server and its two workers all bump them
public final class AsyncLightStats {
    public static final LongAdder CHUNKS_QUEUED = new LongAdder();
    public static final LongAdder BLOCK_CHANGES = new LongAdder();
    public static final LongAdder SKY_TASKS = new LongAdder();
    public static final LongAdder BLOCK_TASKS = new LongAdder();
    public static final LongAdder INITIAL_LIGHTS = new LongAdder();
    public static final LongAdder POSITIONS_PROCESSED = new LongAdder();
    public static final LongAdder SKY_WORKER_NANOS = new LongAdder();
    public static final LongAdder BLOCK_WORKER_NANOS = new LongAdder();
    public static final LongAdder QUEUE_OVERFLOWS = new LongAdder();

    private AsyncLightStats() {
    }
}
