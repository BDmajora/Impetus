package com.bdmajora.extras.client.bakedentities;

// Tracks the lid animation of a block entity.
public final class LidTracking {
    // Ticks that the renderer stays after the lid settles.
    private static final int RENDERER_GRACE_TICKS = 4;
    // State of a settled lid.
    private static final int SETTLED = 1;
    // State of a new block entity.
    public static final int INITIAL = SETTLED;

    // Prevents instantiation
    private LidTracking() {
    }

    // Returns the state after one tick.
    public static int tick(int state, boolean settled) {
        int grace = state >>> 1;
        if (!settled) {
            grace = RENDERER_GRACE_TICKS;
        } else if (grace > 0) {
            grace--;
        }
        return (grace << 1) | (settled ? SETTLED : 0);
    }

    // Returns whether the rest flag differs.
    public static boolean restChanged(int before, int after) {
        return ((before ^ after) & SETTLED) != 0;
    }

    // Returns whether the renderer is still needed.
    public static boolean needsRenderer(int state, boolean settled) {
        return !settled || (state >>> 1) > 0;
    }
}
