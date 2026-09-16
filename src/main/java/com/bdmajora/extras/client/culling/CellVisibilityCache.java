package com.bdmajora.extras.client.culling;

import java.util.Arrays;

// Per-pass memo of "is the block cell at this offset from the camera reachable"; many targets share cells (a crowd of mobs, a wall of chests), and a full clear per pass is cheaper than stamping. Offsets beyond the radius are simply not cached
public final class CellVisibilityCache {
    private static final byte UNKNOWN = 0;
    private static final byte BLOCKED = 1;
    private static final byte VISIBLE = 2;

    private final int radius;
    private final int size;
    private final byte[] cells;

    public CellVisibilityCache(int radius) {
        this.radius = radius;
        this.size = radius * 2;
        this.cells = new byte[this.size * this.size * this.size];
    }

    public void clear() {
        Arrays.fill(this.cells, UNKNOWN);
    }

    // Returns the cached answer or computes it via `test`, which is only consulted for unknown cells
    public boolean isVisible(int dx, int dy, int dz, CellTest test) {
        int x = dx + this.radius;
        int y = dy + this.radius;
        int z = dz + this.radius;
        if (x < 0 || y < 0 || z < 0 || x >= this.size || y >= this.size || z >= this.size) {
            return test.test();
        }
        int index = (x * this.size + y) * this.size + z;
        byte cached = this.cells[index];
        if (cached != UNKNOWN) {
            return cached == VISIBLE;
        }
        boolean visible = test.test();
        this.cells[index] = visible ? VISIBLE : BLOCKED;
        return visible;
    }

    public interface CellTest {
        boolean test();
    }
}
