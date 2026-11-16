package com.bdmajora.impetus.engine.impl.render.mesh.region;

import java.util.Arrays;
import java.util.function.IntPredicate;
import org.lwjgl.system.MemoryUtil;

// Tracks how often each region was visible to choose regions to evict.
public class RegionVisibilityTracker {
    // Samples a region needs before its history is trusted.
    static final int MIN_SAMPLES = 200;

    // Samples of each region.
    private final int[] samples;
    // Frame in which each region was last visible.
    private final int[] lastVisible;
    // Number of recorded frames.
    private int frame;

    // Creates a tracker and rejects a negative region count.
    public RegionVisibilityTracker(int maxRegions) {
        if (maxRegions < 0) {
            throw new IllegalArgumentException("Negative region count " + maxRegions);
        }
        this.samples = new int[maxRegions];
        this.lastVisible = new int[maxRegions];
    }

    // Records the visibility of the listed regions for one frame.
    public void record(short[] regionIds, int count, long address) {
        this.frame++;

        for (int i = 0; i < count; i++) {
            int regionId = Short.toUnsignedInt(regionIds[i]);
            if (this.samples[regionId] < Integer.MAX_VALUE) {
                this.samples[regionId]++;
            }

            if (MemoryUtil.memGetByte(address + i) != 0) {
                this.lastVisible[regionId] = this.frame;
            }
        }
    }

    // Clears the history of a region.
    public void reset(int regionId) {
        this.samples[regionId] = 0;
        this.lastVisible[regionId] = 0;
    }

    // Returns the accepted region that was seen longest ago or minus one.
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

    // Clears all history.
    public void clear() {
        Arrays.fill(this.samples, 0);
        Arrays.fill(this.lastVisible, 0);
        this.frame = 0;
    }
}
