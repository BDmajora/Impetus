package com.bdmajora.fulgor.async;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RenderBoundsEdgeTest {
    @Test
    void outOfRangeInputsNeverCorruptNeighbouringFields() {
        long packed = RenderBounds.pack(-1, 16, 3, 15, 0, 7);
        assertEquals(15, RenderBounds.minX(packed));
        assertEquals(0, RenderBounds.minY(packed));
        assertEquals(3, RenderBounds.minZ(packed));
        assertEquals(15, RenderBounds.maxX(packed));
        assertEquals(0, RenderBounds.maxY(packed));
        assertEquals(7, RenderBounds.maxZ(packed));
    }

    @Test
    void unionTakesTheExtremes() {
        long a = RenderBounds.pack(2, 3, 4, 5, 6, 7);
        long b = RenderBounds.pack(1, 5, 4, 9, 6, 6);
        long u = RenderBounds.union(a, b);
        assertEquals(1, RenderBounds.minX(u));
        assertEquals(3, RenderBounds.minY(u));
        assertEquals(4, RenderBounds.minZ(u));
        assertEquals(9, RenderBounds.maxX(u));
        assertEquals(6, RenderBounds.maxY(u));
        assertEquals(7, RenderBounds.maxZ(u));
    }
}
