package com.bdmajora.extras.client.bakedentities;

// The rest-tracking state machine shared by the lid animation mixins, packed into one int per block entity: bit 0 is whether the lid was at rest last tick, the bits above count the ticks the renderer is kept after rest so the mesh rebuild has landed before it stops
public final class LidTracking {
    private static final int RENDERER_GRACE_TICKS = 4;
    private static final int SETTLED = 1;
    // At rest with no grace outstanding
    public static final int INITIAL = SETTLED;

    private LidTracking() {
    }

    // The state after one tick in which the lid is (or is not) at rest
    public static int tick(int state, boolean settled) {
        int grace = state >>> 1;
        if (!settled) {
            grace = RENDERER_GRACE_TICKS;
        } else if (grace > 0) {
            grace--;
        }
        return (grace << 1) | (settled ? SETTLED : 0);
    }

    // Whether the lid left or returned to rest between the two states, the moment the section must be rebuilt
    public static boolean restChanged(int before, int after) {
        return ((before ^ after) & SETTLED) != 0;
    }

    // True while the renderer must still draw it: mid-animation, or inside the grace after settling
    public static boolean needsRenderer(int state, boolean settled) {
        return !settled || (state >>> 1) > 0;
    }
}
