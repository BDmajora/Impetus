package com.bdmajora.extras.client.culling;

import java.util.Arrays;

// Remembers which cells around the camera were found visible during one pass.
public final class CellVisibilityCache {
    // Answer of a cell that was not tested this pass or lies outside the cube.
    public static final int UNKNOWN = -1;
    // Answer of a hidden cell.
    public static final int BLOCKED = 0;
    // Answer of a visible cell.
    public static final int VISIBLE = 1;
    // Largest pass stamp a cell byte holds next to its visibility bit.
    private static final int MAX_STAMP = 127;

    // Number of cells from the centre to the edge.
    private final int radius;
    // Number of cells along one side.
    private final int size;
    // Pass stamp of each cell shifted left once plus one when the cell was visible.
    private final byte[] cells;
    // Stamp of the current pass.
    private int stamp = 1;

    // Creates a cache and rejects a negative or huge radius.
    public CellVisibilityCache(int radius) {
        if (radius < 0 || radius > 600) {
            throw new IllegalArgumentException("Radius out of range: " + radius);
        }
        this.radius = radius;
        this.size = radius * 2;
        this.cells = new byte[this.size * this.size * this.size];
    }

    // Forgets every cell and wipes the array when the stamp wraps.
    public void clear() {
        if (++this.stamp > MAX_STAMP) {
            Arrays.fill(this.cells, (byte) 0);
            this.stamp = 1;
        }
    }

    // Returns VISIBLE or BLOCKED for a cell tested this pass and UNKNOWN for any other.
    public int get(int dx, int dy, int dz) {
        int index = this.indexOf(dx, dy, dz);
        if (index < 0) {
            return UNKNOWN;
        }
        int cell = this.cells[index] & 0xFF;
        return (cell >>> 1) == this.stamp ? cell & 1 : UNKNOWN;
    }

    // Records the answer of a cell inside the cube.
    public void put(int dx, int dy, int dz, boolean visible) {
        int index = this.indexOf(dx, dy, dz);
        if (index >= 0) {
            this.cells[index] = (byte) ((this.stamp << 1) | (visible ? VISIBLE : BLOCKED));
        }
    }

    // Returns the array index of a cell or -1 when it lies outside the cube.
    private int indexOf(int dx, int dy, int dz) {
        int x = dx + this.radius;
        int y = dy + this.radius;
        int z = dz + this.radius;
        if (x < 0 || y < 0 || z < 0 || x >= this.size || y >= this.size || z >= this.size) {
            return -1;
        }
        return (x * this.size + y) * this.size + z;
    }
}
