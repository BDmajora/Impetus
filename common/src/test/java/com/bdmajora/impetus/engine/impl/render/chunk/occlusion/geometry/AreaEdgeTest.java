package com.bdmajora.impetus.engine.impl.render.chunk.occlusion.geometry;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// Tests rectangle keys and the rectangle search.
class AreaEdgeTest {
    // Returns a grid with one set cell.
    private static long[] cell(int x, int y) {
        long[] bits = new long[4];
        bits[y >> 2] |= 1L << (((y & 3) << 4) + x);
        return bits;
    }

    // Round trips every key of a rectangle.
    @Test
    void keys() {
        for (int x0 = 0; x0 < 16; x0 += 5) {
            for (int y0 = 0; y0 < 16; y0 += 5) {
                int key = Area.areaKey(x0, y0, 15, 15);
                assertEquals(x0, Area.x0(key));
                assertEquals(y0, Area.y0(key));
                assertEquals(15, Area.x1(key));
                assertEquals(15, Area.y1(key));
                assertEquals((16 - x0) * (16 - y0), Area.size(key));
                assertEquals(32 - x0 - y0, Area.edgeCount(key));
                assertEquals(key, Area.indexToKey(Area.keyToIndex(key)));
            }
        }
        assertEquals(256, Area.size(Area.indexToKey(0)));
        assertTrue(Area.AREA_COUNT > 0 && Area.SECTION_COUNT > 0);
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> Area.keyToIndex(0x10000));
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> Area.keyToIndex(-1));
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> Area.indexToKey(Area.AREA_COUNT));
    }

    // Tests inclusion and intersection and clearing against a full grid.
    @Test
    void sampleOperations() {
        long[] full = {-1L, -1L, -1L, -1L};
        long[] empty = new long[4];
        for (int i = 0; i < Area.AREA_COUNT; i += 97) {
            assertTrue(Area.isIncludedBySample(full, 0, i));
            assertFalse(Area.isAdditive(full, 0, i));
            assertTrue(Area.intersectsWithSample(full, 0, i));
            assertFalse(Area.isIncludedBySample(empty, 0, i));
            assertTrue(Area.isAdditive(empty, 0, i));
            assertFalse(Area.intersectsWithSample(empty, 0, i));
            long[] copy = full.clone();
            Area.clearBits(copy, 0, i);
            assertFalse(Area.intersectsWithSample(copy, 0, i));
        }
        long[] shifted = new long[8];
        System.arraycopy(full, 0, shifted, 4, 4);
        assertTrue(Area.isIncludedBySample(shifted, 4, 0));
        assertFalse(Area.isIncludedBySample(shifted, 0, 0));
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> Area.isIncludedBySample(full, 1, 0));
    }

    // Finds the single cell and the full grid and a rectangle.
    @Test
    void findLargest() {
        for (int[] c : new int[][]{{0, 0}, {15, 15}, {7, 12}, {15, 0}, {0, 15}}) {
            int key = AreaFinder.findLargest(cell(c[0], c[1]));
            assertEquals(1, Area.size(key));
            assertEquals(c[0], Area.x0(key));
            assertEquals(c[1], Area.y0(key));
        }
        assertEquals(256, Area.size(AreaFinder.findLargest(new long[]{-1L, -1L, -1L, -1L})));
        assertThrows(IllegalArgumentException.class, () -> AreaFinder.findLargest(new long[3]));
        assertThrows(NullPointerException.class, () -> AreaFinder.findLargest(null));
    }

    // Covers every set cell exactly once and reports no area for an empty grid.
    @Test
    void findCoversEveryCell() {
        AreaFinder finder = new AreaFinder();
        long[] bits = {0x00F0_00F0_00F0_00F0L, 0xFFFF_0000_0000_FFFFL, 0x1L, 0x8000_0000_0000_0000L};
        int expected = 0;
        for (long b : bits) expected += Long.bitCount(b);
        List<Integer> found = new ArrayList<>();
        finder.find(bits, 0, found::add);
        int covered = 0;
        for (int index : found) covered += Area.size(Area.indexToKey(index));
        assertEquals(expected, covered);
        List<Integer> none = new ArrayList<>();
        finder.find(new long[4], 0, none::add);
        assertTrue(none.isEmpty());
        assertThrows(NullPointerException.class, () -> finder.find(bits, 0, null));
        assertThrows(IndexOutOfBoundsException.class, () -> finder.find(bits, 1, i -> {}));
        assertThrows(IndexOutOfBoundsException.class, () -> finder.find(bits, -1, i -> {}));
        assertThrows(IndexOutOfBoundsException.class, () -> finder.findSections(bits, 3, i -> {}));
    }

    // Reports the section rectangles of a full grid and nothing for an empty one.
    @Test
    void findSections() {
        AreaFinder finder = new AreaFinder();
        List<Integer> found = new ArrayList<>();
        finder.findSections(new long[]{-1L, -1L, -1L, -1L}, 0, found::add);
        assertEquals(Area.SECTION_COUNT, found.size());
        found.clear();
        finder.findSections(new long[4], 0, found::add);
        assertTrue(found.isEmpty());
        assertEquals(Area.bitsFromIndex(0, 2), finder.bitsFromIndex(0)[2]);
        assertSame(finder.bitsFromIndex(0), finder.bitsFromIndex(1));
    }

    // Rejects a null rectangle finder.
    @Test
    void boxFinderNull() {
        assertThrows(NullPointerException.class, () -> new BoxFinder(null));
    }
}
