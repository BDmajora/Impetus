package com.bdmajora.impetus.engine.impl.render.chunk.sorting;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// Tests the plane index and the sort states at their limits.
class SortingEdgeTest {
    // Returns cuts for a single plane on the x axis.
    private static float[][] xCut(float cut) {
        return new float[][]{{cut}, {}, {}};
    }

    // Finds owners with a plane between two positions and ignores empty movement.
    @Test
    void queryBasics() {
        CutPlaneIndex<String> index = new CutPlaneIndex<>();
        String a = "a";
        index.put(a, xCut(5f), 0, 0, 0);
        assertEquals(1, index.size());
        assertEquals(1, index.planeCount());
        List<String> hits = new ArrayList<>();
        index.query(0, 0, 0, 10, 0, 0, hits::add);
        assertEquals(List.of("a"), hits);
        hits.clear();
        index.query(10, 0, 0, 0, 0, 0, hits::add);
        assertEquals(List.of("a"), hits);
        hits.clear();
        index.query(6, 0, 0, 20, 0, 0, hits::add);
        index.query(5, 0, 0, 5, 0, 0, hits::add);
        index.query(0, 0, 0, 4.9, 0, 0, hits::add);
        assertTrue(hits.isEmpty());
        index.remove(a);
        index.remove(a);
        assertEquals(0, index.size());
        assertEquals(0, index.planeCount());
    }

    // Terminates for infinite and not a number coordinates.
    @Test
    void queryExtremes() {
        CutPlaneIndex<String> index = new CutPlaneIndex<>();
        index.put("a", xCut(5f), 0, 0, 0);
        List<String> hits = new ArrayList<>();
        index.query(Double.NEGATIVE_INFINITY, 0, 0, Double.POSITIVE_INFINITY, 0, 0, hits::add);
        assertEquals(List.of("a"), hits);
        hits.clear();
        index.query(Double.NaN, 0, 0, 10, 0, 0, hits::add);
        index.query(0, Double.NaN, Double.NaN, 0, Double.NaN, Double.NaN, hits::add);
        assertTrue(hits.isEmpty());
        index.query(-1e300, 0, 0, 1e300, 0, 0, hits::add);
        assertEquals(List.of("a"), hits);
        hits.clear();
        index.query(0, 0, 0, 1000, 0, 0, hits::add);
        assertEquals(List.of("a"), hits);
    }

    // Reports each owner once across axes and uses an origin and ignores leaves.
    @Test
    void ownersAreReportedOnce() {
        CutPlaneIndex<String> index = new CutPlaneIndex<>();
        index.put("a", new float[][]{{1f}, {1f}, {1f}}, 100, 100, 100);
        index.put("leaf", new float[][]{{}, {}, {}}, 0, 0, 0);
        assertEquals(1, index.size());
        List<String> hits = new ArrayList<>();
        index.query(99, 99, 99, 102, 102, 102, hits::add);
        assertEquals(List.of("a"), hits);
        index.put("a", xCut(1f), 100, 100, 100);
        assertEquals(1, index.planeCount());
    }

    // Rejects bad input and still clears its state when the consumer fails.
    @Test
    void refusals() {
        CutPlaneIndex<String> index = new CutPlaneIndex<>();
        assertThrows(NullPointerException.class, () -> index.put(null, xCut(1f), 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> index.put("a", new float[2][0], 0, 0, 0));
        assertThrows(NullPointerException.class, () -> index.put("a", new float[][]{{}, null, {}}, 0, 0, 0));
        assertThrows(NullPointerException.class, () -> index.query(0, 0, 0, 1, 1, 1, null));
        index.put("a", xCut(1f), 0, 0, 0);
        assertThrows(IllegalStateException.class, () -> index.query(0, 0, 0, 2, 0, 0, o -> {
            throw new IllegalStateException();
        }));
        List<String> hits = new ArrayList<>();
        index.query(0, 0, 0, 2, 0, 0, hits::add);
        assertEquals(List.of("a"), hits);
    }

    // Validates sort state data.
    @Test
    void sortStates() {
        assertThrows(NullPointerException.class, () -> new SortState.Static(null));
        assertThrows(IllegalArgumentException.class, () -> new SortState.Dynamic(new float[4]));
        assertEquals(2, new SortState.Dynamic(new float[6]).quadCount());
        assertEquals(0, new SortState.Dynamic(new float[0]).quadCount());
        assertEquals(3, new SortState.Static(new int[3]).quadCount());
        assertSame(SortState.NONE, new SortState.Static(new int[3]).compactForStorage());
        SortState.Dynamic dynamic = new SortState.Dynamic(new float[3]);
        assertSame(dynamic, dynamic.compactForStorage());
        assertTrue(dynamic.requiresDynamicSorting());
        assertFalse(SortState.NONE.requiresDynamicSorting());
        assertEquals(4, SortState.DEBUG_NAMES.length);
        assertEquals(0, SortState.NONE.debugIndex());
        assertEquals(0, SortState.NONE.quadCount());
    }

    // Validates quad set array lengths.
    @Test
    void quadSetValidation() {
        assertThrows(IllegalArgumentException.class, () -> new QuadSet(-1, new float[0], new float[0], new float[0], new float[0], new byte[0]));
        assertThrows(IllegalArgumentException.class, () -> new QuadSet(1, new float[2], new float[6], new float[3], new float[1], new byte[1]));
        assertThrows(IllegalArgumentException.class, () -> new QuadSet(1, new float[3], new float[5], new float[3], new float[1], new byte[1]));
        assertThrows(IllegalArgumentException.class, () -> new QuadSet(1, new float[3], new float[6], new float[2], new float[1], new byte[1]));
        assertThrows(IllegalArgumentException.class, () -> new QuadSet(1, new float[3], new float[6], new float[3], new float[0], new byte[1]));
        assertThrows(IllegalArgumentException.class, () -> new QuadSet(1, new float[3], new float[6], new float[3], new float[1], new byte[0]));
        assertThrows(IllegalArgumentException.class, () -> new QuadSet(Integer.MAX_VALUE, new float[3], new float[6], new float[3], new float[1], new byte[1]));
        QuadSet empty = new QuadSet(0, new float[0], new float[0], new float[0], new float[0], new byte[0]);
        QuadSet.Bounds union = empty.union();
        assertEquals(Float.POSITIVE_INFINITY, union.minX());
        assertEquals(Float.NEGATIVE_INFINITY, union.maxX());
    }

    // Projects bounds onto directions of either sign.
    @Test
    void dotProjections() {
        QuadSet set = new QuadSet(1, new float[]{1, 1, 1}, new float[]{0, 0, 0, 2, 2, 2}, new float[]{0, 0, 1}, new float[]{1}, new byte[]{0});
        assertEquals(0f, set.minDot(0, 1, 1, 1));
        assertEquals(6f, set.maxDot(0, 1, 1, 1));
        assertEquals(-6f, set.minDot(0, -1, -1, -1));
        assertEquals(0f, set.maxDot(0, -1, -1, -1), 1e-6f);
        assertEquals(0f, set.boundsMin(0, 2));
        assertEquals(2f, set.boundsMax(0, 0));
        assertFalse(set.isDegenerate(0));
        assertEquals(1f, set.normalDot(0, 0));
        assertTrue(set.isCoplanar(0, 0));
        assertEquals(1f, set.normalZ(0));
        assertEquals(0f, set.normalX(0), 1e-6f);
        assertEquals(0f, set.normalY(0), 1e-6f);
        assertEquals(0f, set.union().minDot(1, 1, 1));
    }

    // Rejects a count whose array lengths wrap around the int range.
    @Test
    void quadSetRejectsWrappedLengths() {
        int count = 1_431_655_766;
        assertThrows(IllegalArgumentException.class, () -> new QuadSet(count, new float[2], new float[4], new float[2], new float[0], new byte[0]));
        assertThrows(IllegalArgumentException.class, () -> new QuadSet(-1, new float[0], new float[0], new float[0], new float[0], new byte[0]));
        QuadSet empty = new QuadSet(0, new float[0], new float[0], new float[0], new float[0], new byte[0]);
        assertEquals(Float.POSITIVE_INFINITY, empty.union().minX());
    }
}
