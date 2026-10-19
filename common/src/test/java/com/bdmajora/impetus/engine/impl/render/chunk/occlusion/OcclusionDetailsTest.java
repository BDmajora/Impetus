package com.bdmajora.impetus.engine.impl.render.chunk.occlusion;

import com.bdmajora.impetus.engine.impl.render.chunk.ChunkUpdateType;
import com.bdmajora.impetus.engine.impl.render.viewport.frustum.Frustum;
import com.bdmajora.impetus.engine.impl.render.viewport.frustum.SimpleFrustum;
import com.bdmajora.testing.Sections;
import grondag.bitraster.PackedBox;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OcclusionDetailsTest {
    @Test
    void budgetCountersAndCopiesDescribeTheActualTestedWork() {
        RasterBudget budget = new RasterBudget();
        budget.beginFrame(1000);
        for (int i = 0; i < 40; i++) {
            boolean tested = budget.shouldTest(RasterBudget.FLOOR + 1);
            if (tested && i < 8) budget.recordCulled();
        }
        assertEquals(40, budget.farReached());
        assertEquals(RasterBudget.CHEAP_PASS, budget.tested());
        assertEquals(8, budget.culled());
        assertFalse(budget.wasFullPass());
        assertFalse(budget.wasProbe());
        budget.endFrame(32);
        assertTrue(budget.yield() > 0);
        assertTrue(budget.toString().contains("tested=32 culled=8"));
        RasterBudget copy = new RasterBudget();
        copy.copyFrom(budget);
        assertEquals(budget.limit(), copy.limit());
        assertEquals(budget.yield(), copy.yield());
        copy.pin(RasterBudget.UNBOUNDED);
        copy.beginFrame(Integer.MAX_VALUE);
        assertTrue(copy.shouldTest(Integer.MAX_VALUE));
        assertTrue(copy.wasFullPass());
        assertEquals(RasterBudget.UNBOUNDED, copy.testableLimit());
        assertTrue(copy.toString().contains("limit=max"));
        copy.pin(0);
        for (int frame = 0; frame < RasterBudget.PROBE_PERIOD * 2; frame++) {
            budget.beginFrame(1000);
            for (int i = 0; i < 16; i++) budget.shouldTest(1);
            budget.endFrame(100);
        }
    }

    @Test
    void regionClassificationsGrowForLargeWorldsAndResetForEachSearch() {
        RegionCullCache cache = new RegionCullCache();
        cache.begin(Sections.viewport(8, 8, 8), 100, 2048);
        assertEquals(RegionCullCache.UNCOMPUTED, cache.cached(2047));
        int result = cache.classify(2047, 0, 0, 0);
        assertEquals(result, cache.cached(2047));
        cache.begin(Sections.viewport(8, 8, 8), 100, 2048);
        assertEquals(RegionCullCache.UNCOMPUTED, cache.cached(2047));
        assertNotNull(new FastFrustumClamping());
    }

    @Test
    void shadowQueueGrowthKeepsPendingEntriesAndNewFacesWidenOnlyBlockedGraphs() throws Exception {
        ShadowOcclusionCuller culler = new ShadowOcclusionCuller(new SectionLattice(0, 1, true, false));
        var field = ShadowOcclusionCuller.class.getDeclaredField("queue");
        field.setAccessible(true);
        long[] queue = (long[]) field.get(culler);
        queue[0] = 42;
        var grow = ShadowOcclusionCuller.class.getDeclaredMethod("grow", int.class);
        grow.setAccessible(true);
        long[] expanded = (long[]) grow.invoke(culler, 1024);
        assertTrue(expanded.length >= 1024);
        assertEquals(42, expanded[0]);
        var widens = ShadowOcclusionCuller.class.getDeclaredMethod("widensExits", long.class, long.class, int.class, int.class, int.class);
        widens.setAccessible(true);
        int west = 1 << GraphDirection.WEST;
        int east = 1 << GraphDirection.EAST;
        long graph = VisibilityEncoding.encode((from, to) -> from == GraphDirection.WEST && to == GraphDirection.NORTH
                || from == GraphDirection.EAST && to == GraphDirection.SOUTH);
        assertEquals(true, widens.invoke(null, graph, VisibilityEncoding.EVERYTHING, GraphDirectionSet.ALL, west, east));
        assertEquals(false, widens.invoke(null, graph, VisibilityEncoding.EVERYTHING, GraphDirectionSet.ALL, west, west));
        assertEquals(false, widens.invoke(null, VisibilityEncoding.EVERYTHING, VisibilityEncoding.EVERYTHING, GraphDirectionSet.ALL, west, east));
    }

    @Test
    void frustumClassificationDistinguishesContainedIntersectingAndRejectedBounds() {
        SimpleFrustum frustum = new SimpleFrustum(new FrustumIntersection(new Matrix4f()));
        assertEquals(Frustum.FULLY_INSIDE, frustum.intersectAab(-0.5f, -0.5f, -0.5f, 0.5f, 0.5f, 0.5f));
        assertEquals(Frustum.PARTIALLY_INSIDE, frustum.intersectAab(0.5f, -0.5f, -0.5f, 2, 0.5f, 0.5f));
        assertEquals(Frustum.OUTSIDE, frustum.intersectAab(2, 2, 2, 3, 3, 3));
        assertTrue(ChunkUpdateType.canPromote(null, ChunkUpdateType.SORT));
        assertTrue(ChunkUpdateType.canPromote(ChunkUpdateType.REBUILD, ChunkUpdateType.IMPORTANT_REBUILD));
        assertFalse(ChunkUpdateType.canPromote(ChunkUpdateType.IMPORTANT_REBUILD, ChunkUpdateType.SORT));
        RasterOccluder raster = new RasterOccluder();
        raster.prepareScene(1, new com.bdmajora.impetus.engine.impl.render.viewport.Viewport(Sections.EVERYTHING, new org.joml.Vector3d(), new Matrix4f()), 100);
        raster.occludeSectionAt(0, 0, 32, new int[] {PackedBox.FULL_BOX, PackedBox.FULL_BOX});
    }
}
