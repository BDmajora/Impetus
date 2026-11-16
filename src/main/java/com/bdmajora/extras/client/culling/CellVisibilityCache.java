package com.bdmajora.extras.client.culling;

import java.util.Arrays;

// Remembers which cells around the camera are visible.
public final class CellVisibilityCache {
    // State of a cell that was not tested.
    private static final byte UNKNOWN = 0;
    // State of a cell that is hidden.
    private static final byte BLOCKED = 1;
    // State of a cell that is visible.
    private static final byte VISIBLE = 2;

    // Number of cells from the centre to the edge.
    private final int radius;
    // Number of cells along one side.
    private final int size;
    // State of every cell.
    private final byte[] cells;

    // Creates a cache and rejects a negative or huge radius.
    public CellVisibilityCache(int radius) {
        if (radius < 0 || radius > 600) {
            throw new IllegalArgumentException("Radius out of range: " + radius);
        }
        this.radius = radius;
        this.size = radius * 2;
        this.cells = new byte[this.size * this.size * this.size];
    }

    // Forgets every cell.
    public void clear() {
        Arrays.fill(this.cells, UNKNOWN);
    }

    // Returns the cached result of the cell and runs the test for a new cell.
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

    // Test of one cell.
    public interface CellTest {
        // Returns whether the cell is visible.
        boolean test();
    }
}
