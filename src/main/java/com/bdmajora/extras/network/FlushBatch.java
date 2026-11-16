package com.bdmajora.extras.network;

import com.bdmajora.extras.Extras;

// Batcher that holds network flushes until the end of a tick.
public final class FlushBatch {
    // Constant in tick.
    private static final ThreadLocal<Boolean> IN_TICK = ThreadLocal.withInitial(() -> Boolean.FALSE);

    // Prevents instantiation
    private FlushBatch() {
    }

    // Returns whether defer.
    public static boolean shouldDefer() {
        return IN_TICK.get() && Extras.options().network.flushConsolidation;
    }

    // Starts holding flushes for this tick.
    public static void beginTick() {
        IN_TICK.set(Boolean.TRUE);
    }

    // Stops holding flushes and runs the held ones.
    public static void endTick() {
        IN_TICK.set(Boolean.FALSE);
    }

    // Contract of the deferrable.
    public interface Deferrable {
        // Runs a held flush.
        void impetus$flushDeferred();
    }
}
