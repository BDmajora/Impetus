package com.bdmajora.impetus.engine.impl.render.chunk.sorting;

import com.bdmajora.impetus.engine.impl.model.quad.properties.ModelQuadFacing;
import com.bdmajora.impetus.engine.impl.render.chunk.vertex.format.ChunkVertexEncoder;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.bdmajora.testing.Quads.*;
import static org.junit.jupiter.api.Assertions.*;

class TranslucentSorterTest {
    private static final float[][] UNIT_UP = facingY(0, 0, 1, 0, 1);

    @Test
    void shallowWaterSlopeAndFlatFaceStayOnSeparatePlanes() {
        float[][] slope = facingY(0, 0, 1, 0, 1);
        for (float[] vertex : slope) {
            vertex[1] = (vertex[0] - 0.5f) * 0.01f;
        }
        QuadSet quads = set(UNIT_UP, slope);
        assertFalse(quads.isCoplanar(0, 1));
        assertFalse(quads.hasParallelNormal(0, 1, quads.normalDot(0, 1)));
        assertNotSame(SortState.NONE, analyze(UNIT_UP, slope));
    }

    private static SortState analyze(float[][]... quads) {
        return record(quads).getSortState();
    }

    private static float[][][] concat(float[][][] a, float[][][] b) {
        float[][][] all = new float[a.length + b.length][][];
        System.arraycopy(a, 0, all, 0, a.length);
        System.arraycopy(b, 0, all, a.length, b.length);
        return all;
    }

    @Test
    void theRecorderCapturesCentresBoundsNormalsAndFacings() {
        TranslucentQuadRecorder recorder = record(facingY(2, 0, 1, 0, 1), facingX(0, 0, 1, 0, 1), degenerate(3, 3, 3));
        // A partial quad is held back until its fourth corner arrives
        recorder.capture(new ChunkVertexEncoder.Vertex());
        QuadSet quads = recorder.toQuadSet();
        assertEquals(3, quads.count());
        assertArrayEquals(new float[]{0.5f, 2, 0.5f}, java.util.Arrays.copyOf(quads.centers(), 3));
        assertArrayEquals(new float[]{0, 2, 0, 1, 2, 1}, java.util.Arrays.copyOf(quads.bounds(), 6));
        assertEquals(1, quads.normalY(0));
        assertEquals(2, quads.dots()[0]);
        assertEquals(ModelQuadFacing.POS_Y, quads.facing(0));
        assertEquals(1, quads.normalX(1));
        assertEquals(0, quads.normalZ(1), 1e-6);
        assertEquals(ModelQuadFacing.POS_X, quads.facing(1));
        assertTrue(quads.isDegenerate(2));
        assertFalse(quads.isDegenerate(0));
        assertEquals(ModelQuadFacing.UNASSIGNED, quads.facing(2));
        assertEquals(ModelQuadFacing.NEG_Y, set(flipped(UNIT_UP)).facing(0));
        recorder.clear();
        assertEquals(0, recorder.toQuadSet().count());
        assertSame(SortState.NONE, recorder.getSortState());
    }

    @Test
    void quadSetsRejectMismatchedArraysAndMeasureTheirBounds() {
        assertThrows(IllegalArgumentException.class, () -> new QuadSet(-1, new float[0], new float[0], new float[0], new float[0], new byte[0]));
        assertThrows(IllegalArgumentException.class, () -> new QuadSet(1, new float[2], new float[6], new float[3], new float[1], new byte[1]));
        assertThrows(IllegalArgumentException.class, () -> new QuadSet(1, new float[3], new float[5], new float[3], new float[1], new byte[1]));
        assertThrows(IllegalArgumentException.class, () -> new QuadSet(1, new float[3], new float[6], new float[2], new float[1], new byte[1]));
        assertThrows(IllegalArgumentException.class, () -> new QuadSet(1, new float[3], new float[6], new float[3], new float[0], new byte[1]));
        assertThrows(IllegalArgumentException.class, () -> new QuadSet(1, new float[3], new float[6], new float[3], new float[1], new byte[0]));

        QuadSet quads = set(facingY(1, 0, 2, 0, 3), facingX(-1, 4, 5, -2, 0));
        // Returns the bound corners.
        assertEquals(0, quads.minDot(0, 1, 0, 0), 1e-6);
        assertEquals(-2, quads.minDot(0, -1, 0, 0), 1e-6);
        assertEquals(-1, quads.minDot(0, 0, -1, 0), 1e-6);
        assertEquals(-3, quads.minDot(0, 0, 0, -1), 1e-6);
        assertEquals(2, quads.maxDot(0, 1, 0, 0), 1e-6);
        assertEquals(4, quads.maxDot(0, 0, 1, 1), 1e-6);
        assertEquals(0, quads.maxDot(0, -1, 0, 0), 1e-6);
        assertEquals(-1, quads.maxDot(0, 0, -1, 0), 1e-6);
        assertEquals(0, quads.maxDot(0, 0, 0, -1), 1e-6);
        assertEquals(-1, quads.boundsMin(1, 0), 1e-6);
        assertEquals(5, quads.boundsMax(1, 1), 1e-6);
        QuadSet.Bounds union = quads.union();
        assertEquals(new QuadSet.Bounds(-1, 1, -2, 2, 5, 3), union);
        assertEquals(-1, union.minDot(1, 0, 0), 1e-6);
        assertEquals(-2, union.minDot(-1, 0, 0), 1e-6);
        assertEquals(-5, union.minDot(0, -1, 0), 1e-6);
        assertEquals(-3, union.minDot(0, 0, -1), 1e-6);
        assertEquals(3, union.maxDot(0, 0, 1), 1e-6);
        assertEquals(2, union.maxDot(0, 0, -1), 1e-6);
        assertEquals(1, union.maxDot(-1, 0, 0), 1e-6);
        assertEquals(-1, union.maxDot(0, -1, 0), 1e-6);

        // Checks coplanar faces.
        QuadSet pair = set(UNIT_UP, flipped(UNIT_UP), facingY(3, 0, 1, 0, 1), facingX(0, 0, 1, 0, 1));
        assertTrue(pair.isCoplanar(0, 1));
        assertFalse(pair.isCoplanar(0, 2));
        assertFalse(pair.isCoplanar(0, 3));
        assertEquals(-1, pair.normalDot(0, 1));
        assertFalse(pair.isSeenThrough(0, 1));
    }

    @Test
    void analysisPicksTheCheapestSortThatIsCorrect() {
        assertSame(SortState.NONE, analyze());
        assertSame(SortState.NONE, analyze(degenerate(0, 0, 0), degenerate(1, 2, 3)));
        // A degenerate quad is skipped when deciding the rest are parallel
        assertInstanceOf(SortState.Static.class, analyze(degenerate(0, 0, 0), UNIT_UP, flipped(facingY(1, 0, 1, 0, 1))));
        // The outside faces of a block can never overlap on screen
        assertSame(SortState.NONE, analyze(facingY(1, 0, 1, 0, 1), flipped(UNIT_UP), facingX(1, 0, 1, 0, 1), flipped(facingX(0, 0, 1, 0, 1))));

        // Checks parallel faces.
        SortState.Static inward = assertInstanceOf(SortState.Static.class, analyze(UNIT_UP, flipped(facingY(1, 0, 1, 0, 1))));
        assertArrayEquals(new int[]{0, 1}, inward.order());
        SortState.Static stacked = assertInstanceOf(SortState.Static.class, analyze(facingY(3, 0, 1, 0, 1), UNIT_UP));
        assertArrayEquals(new int[]{1, 0}, stacked.order());
        assertEquals(2, stacked.quadCount());

        // Checks one behind the other.
        SortState.Static ordered = assertInstanceOf(SortState.Static.class, analyze(facingY(1, 0, 1, 0, 1), facingX(0, 0, 1, 0, 1)));
        assertArrayEquals(new int[]{1, 0}, ordered.order());

        // Checks overlapping quads.
        PartitionTree split = assertInstanceOf(PartitionTree.class, analyze(straddling(0)));
        assertArrayEquals(new float[]{1}, split.cutPlanes(2));
        assertEquals(0, split.cutPlanes(0).length);
        // Checks crossing quads.
        PartitionTree crossed = assertInstanceOf(PartitionTree.class, analyze(facingY(0, 0, 2, 0, 1), facingX(1, -1, 1, 0, 1)));
        assertArrayEquals(new int[]{0, 1}, crossed.order(0, 9, 0));
        assertArrayEquals(new int[]{0, 1}, crossed.order(9, -9, 9));
        // A cycle with no cut and nothing touching can only be sorted by distance every frame
        SortState.Dynamic pinwheel = assertInstanceOf(SortState.Dynamic.class, analyze(pinwheel(0)));
        assertEquals(3, pinwheel.quadCount());
    }

    @Test
    void sortStatesReportTheirCostAndWhatSurvivesCompaction() {
        assertEquals(0, SortState.NONE.debugIndex());
        assertEquals(0, SortState.NONE.quadCount());
        assertSame(SortState.NONE, new SortState.None().compactForStorage());
        assertFalse(SortState.NONE.requiresDynamicSorting());
        SortState.Static statik = new SortState.Static(new int[]{1, 0});
        assertEquals("STATIC", SortState.DEBUG_NAMES[statik.debugIndex()]);
        // A static order is already in the index buffer written at build time
        assertSame(SortState.NONE, statik.compactForStorage());
        assertFalse(statik.requiresDynamicSorting());
        SortState.Dynamic dynamic = new SortState.Dynamic(new float[6]);
        assertEquals("DYNAMIC", SortState.DEBUG_NAMES[dynamic.debugIndex()]);
        assertEquals(2, dynamic.quadCount());
        assertSame(dynamic, dynamic.compactForStorage());
        assertTrue(dynamic.requiresDynamicSorting());
        PartitionTree tree = PartitionTree.build(set(straddling(0)));
        assertEquals("TREE", SortState.DEBUG_NAMES[tree.debugIndex()]);
        assertEquals(2, tree.quadCount());
        assertSame(tree, tree.compactForStorage());
        assertTrue(tree.requiresDynamicSorting());
    }

    @Test
    void ordersFollowTheCameraFarthestFirst() {
        float[] centers = {0, 0, 0, 10, 0, 0, 4, 0, 0};
        assertArrayEquals(new int[]{1, 2, 0}, TranslucentSorter.sortByDistance(centers, 3, -1, 0, 0));
        assertArrayEquals(new int[]{0, 2, 1}, TranslucentSorter.sortByDistance(centers, 3, 11, 0, 0));
        assertNull(TranslucentSorter.order(SortState.NONE, 0, 0, 0));
        assertArrayEquals(new int[]{1, 0}, TranslucentSorter.order(new SortState.Static(new int[]{1, 0}), 0, 0, 0));
        assertArrayEquals(new int[]{0, 2, 1}, TranslucentSorter.order(new SortState.Dynamic(centers), 11, 0, 0));
        PartitionTree tree = PartitionTree.build(set(straddling(0)));
        assertArrayEquals(new int[]{1, 0}, TranslucentSorter.order(tree, 0, 0, 0.5f));
        // Facing-away quads (flipped) sort after the ones facing the fake camera
        BitSet flipped = new BitSet();
        flipped.set(0);
        float[] parallel = {0, 0, 0, 0, 5, 0};
        assertArrayEquals(new int[]{0, 1}, TranslucentSorter.sortNormalRelative(parallel, 2, 0, 1, 0, new BitSet()));
        assertArrayEquals(new int[]{1, 0}, TranslucentSorter.sortNormalRelative(parallel, 2, 0, 1, 0, flipped));
        // The topological sort finds the cycle the analysis then hands to the tree
        assertNull(TranslucentSorter.topologicalOrder(set(straddling(0))));
        assertArrayEquals(new int[]{1, 0}, TranslucentSorter.topologicalOrder(set(facingY(1, 0, 1, 0, 1), facingX(0, 0, 1, 0, 1))));
    }

    @Test
    void partitionTreesReorderOnlyAcrossTheirCutPlanes() {
        PartitionTree tree = PartitionTree.build(set(straddling(0)));
        // The camera's own slab draws last
        assertArrayEquals(new int[]{1, 0}, tree.order(0, 0, 0.5f));
        assertArrayEquals(new int[]{0, 1}, tree.order(0, 0, 5));
        List<int[]> streamed = new ArrayList<>();
        tree.order(0, 0, 5, streamed::add);
        assertEquals(3, streamed.size());
        assertArrayEquals(new int[0], streamed.get(1));
        assertTrue(tree.crossesCutPlane(0, 0, 0.5, 0, 0, 5));
        assertTrue(tree.crossesCutPlane(0, 0, 5, 0, 0, 0.9));
        // Checks half open planes.
        assertTrue(tree.crossesCutPlane(0, 0, 0.5, 0, 0, 1));
        assertArrayEquals(new int[]{1, 0}, tree.order(0, 0, Math.nextDown(1f)));
        assertArrayEquals(new int[]{0, 1}, tree.order(0, 0, 1));
        assertFalse(tree.crossesCutPlane(0, 0, 1, 0, 0, 2));
        assertFalse(tree.crossesCutPlane(-9, -9, 0.5, 9, 9, 0.9));

        PartitionTree farther = PartitionTree.build(set(facingY(0, 0, 3, 0, 2), facingX(1, -1, 1, 3, 4)));
        float[][] merged = PartitionTree.mergeCutPlanes(List.of(tree, farther, tree));
        assertArrayEquals(new float[]{1, 2}, merged[2]);
        assertEquals(0, merged[0].length);

        // Checks no static leaves.
        PartitionTree onPlane = PartitionTree.build(set(facingY(1, 0, 1, 0, 1), facingX(0, 0, 1, 0, 1)), 0);
        assertArrayEquals(new float[]{0}, onPlane.cutPlanes(0));
        assertArrayEquals(new int[]{1, 0}, onPlane.order(5, 0, 0));
        assertArrayEquals(new int[]{0, 1}, onPlane.order(-5, 0, 0));
        // Crossing quads are split into a fixed order when they cannot be cut either
        PartitionTree fixed = PartitionTree.build(set(facingY(0, 0, 2, 0, 1), facingX(1, -1, 1, 0, 1)), 1);
        assertArrayEquals(new int[]{0, 1}, fixed.order(0, 0, 0));
        // Checks parallel slabs.
        PartitionTree layered = PartitionTree.build(set(UNIT_UP, facingY(2, 0, 1, 0, 1), facingZ(0.5f, 0, 1, -2, -1), facingZ(0.5f, 0, 1, 3, 4)), 0);
        assertArrayEquals(new float[]{0, 2}, layered.cutPlanes(1));
        assertArrayEquals(new int[]{2, 0, 3, 1}, layered.order(0, 1, 0));
        // A cycle with nothing to cut and nothing touching has no tree at all
        assertNull(PartitionTree.build(set(pinwheel(0))));
    }

    @Test
    void slopedQuadsUseTheirCornersForSeeThroughDependencies() {
        float[][] lower = {{0, 0, 0}, {0, 0, 1}, {1, 1, 1}, {1, 1, 0}};
        float[][] upper = {{0, 0.25f, 0}, {0, 0.25f, 1}, {1, 1.25f, 1}, {1, 1.25f, 0}};
        QuadSet quads = set(upper, lower);

        assertFalse(quads.isSeenThrough(0, 1));
        assertTrue(quads.isSeenThrough(1, 0));
        assertArrayEquals(new int[]{1, 0}, TranslucentSorter.topologicalOrder(quads));
    }

    @Test
    void theCutPlaneIndexReportsOwnersWhosePlanesTheCameraCrossed() {
        CutPlaneIndex<String> index = new CutPlaneIndex<>();
        List<String> hits = new ArrayList<>();
        index.query(0, 0, 0, 0, 100, 0, hits::add);
        assertTrue(hits.isEmpty());
        index.put("a", new float[][]{{}, {4, 8}, {}}, 0, 0, 0);
        index.put("b", new float[][]{{2}, {}, {}}, 16, 0, 0);
        // Checks a tree with no cuts.
        index.put("leaf", new float[][]{{}, {}, {}}, 0, 0, 0);
        assertEquals(2, index.size());
        assertEquals(3, index.planeCount());

        index.query(0, 2, 0, 0, 6, 0, hits::add);
        assertEquals(List.of("a"), hits);
        hits.clear();
        index.query(0, 9, 0, 0, 12, 0, hits::add);
        index.query(0, 0, 0, 0, 3, 0, hits::add);
        index.query(5, 5, 5, 5, 5, 5, hits::add);
        assertTrue(hits.isEmpty());
        index.query(17, 0, 0, 19, 0, 0, hits::add);
        assertEquals(List.of("b"), hits);
        hits.clear();
        // Checks each owner once.
        index.query(0, 9, 0, 0, 2, 0, hits::add);
        assertEquals(List.of("a"), hits);
        hits.clear();
        index.query(19, 2, 0, 17, 6, 0, hits::add);
        assertEquals(Set.of("a", "b"), new HashSet<>(hits));
        hits.clear();
        // Checks a long jump.
        index.query(0, -1000, 0, 0, 1000, 0, hits::add);
        assertEquals(List.of("a"), hits);
        hits.clear();

        // Checks re-putting.
        index.put("a", new float[][]{{}, {}, {3}}, 0, 0, 0);
        index.put("c", new float[][]{{}, {}, {3.5f}}, 0, 0, 0);
        assertEquals(3, index.planeCount());
        index.query(0, 2, 0, 0, 6, 0, hits::add);
        assertTrue(hits.isEmpty());
        index.remove("a");
        index.remove("a");
        index.remove("nobody");
        index.query(0, 0, 0, 0, 0, 4, hits::add);
        assertEquals(List.of("c"), hits);
        assertEquals(2, index.size());
        assertEquals(2, index.planeCount());
    }
}
