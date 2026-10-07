package com.bdmajora.impetus.engine.impl.render.mesh.region;

import java.util.Arrays;
import java.util.function.IntPredicate;
import org.lwjgl.system.MemoryUtil;


// Per-region history of how often it was in the frustum and when its box last passed the depth test, fed from downloaded region visibility bytes; picks what to evict when the geometry budget runs out (Nvidium's RegionVisibilityTracker)
public class RegionVisibilityTracker {
    // A region must have been sampled this many frames before its history is trusted enough to evict on
    static final int MIN_SAMPLES = 200;

    private final int[] samples;
    private final int[] lastVisible;
    private int frame;

    public RegionVisibilityTracker(int maxRegions) {
        this.samples = new int[maxRegions];
        this.lastVisible = new int[maxRegions];
    }

    // One frame's result: regionIds in visible-list order, and the visibility byte per entry at address
    public void record(short[] regionIds, int count, long address) {
        this.frame++;

        for (int i = 0; i < count; i++) {
            int regionId = Short.toUnsignedInt(regionIds[i]);
            this.samples[regionId]++;

            if (MemoryUtil.memGetByte(address + i) != 0) {
                this.lastVisible[regionId] = this.frame;
            }
        }
    }

    // Forgets a region whose id was released, so a new region reusing the id starts clean
    public void reset(int regionId) {
        this.samples[regionId] = 0;
        this.lastVisible[regionId] = 0;
    }

    // The well-sampled region seen longest ago among those the filter accepts, or -1
    public int leastRecentlySeen(int maxIndex, IntPredicate candidate) {
        int best = -1;
        int oldest = Integer.MAX_VALUE;

        for (int regionId = 0; regionId < maxIndex; regionId++) {
            if (this.samples[regionId] <= MIN_SAMPLES || !candidate.test(regionId)) {
                continue;
            }

            if (this.lastVisible[regionId] < oldest) {
                oldest = this.lastVisible[regionId];
                best = regionId;
            }
        }

        return best;
    }

    // Drops all history
    public void clear() {
        Arrays.fill(this.samples, 0);
        Arrays.fill(this.lastVisible, 0);
        this.frame = 0;
    }
}
