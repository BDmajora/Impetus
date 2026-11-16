package com.bdmajora.impetus.engine.impl.render.chunk.occlusion;

import grondag.bitraster.PackedBox;

// Bounds of the cells of a section that hold renderable blocks.
final class RenderableBounds {
    // Smallest marked coordinate.
    private int minX = 16, minY = 16, minZ = 16;
    // Largest marked coordinate.
    private int maxX = -1, maxY = -1, maxZ = -1;

    // Grows the bounds to hold a cell.
    void mark(int x, int y, int z) {
        if (x < this.minX) this.minX = x;
        if (y < this.minY) this.minY = y;
        if (z < this.minZ) this.minZ = z;
        if (x > this.maxX) this.maxX = x;
        if (y > this.maxY) this.maxY = y;
        if (z > this.maxZ) this.maxZ = z;
    }

    // Returns whether no cell was marked.
    boolean isEmpty() {
        return this.maxX < 0;
    }

    // Returns the bounds widened by one cell as a packed box or the full box when empty.
    int packed() {
        if (isEmpty()) {
            return PackedBox.FULL_BOX;
        }

        return PackedBox.pack(
                Math.max(0, this.minX - 1), Math.max(0, this.minY - 1), Math.max(0, this.minZ - 1),
                Math.min(16, this.maxX + 2), Math.min(16, this.maxY + 2), Math.min(16, this.maxZ + 2),
                PackedBox.RANGE_EXTREME
        );
    }
}
