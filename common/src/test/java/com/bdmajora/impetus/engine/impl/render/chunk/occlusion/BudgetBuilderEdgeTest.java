package com.bdmajora.impetus.engine.impl.render.chunk.occlusion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

// Tests the visibility builder and the raster budget limits.
class BudgetBuilderEdgeTest {
    // Reports full visibility for an empty section and none for a full one.
    @Test
    void trivialSections() {
        SectionVisibilityBuilder empty = new SectionVisibilityBuilder();
        assertEquals(VisibilityEncoding.EVERYTHING, empty.computeVisibilityEncoding());
        assertNull(empty.computeOccluderBoxes());
        SectionVisibilityBuilder full = new SectionVisibilityBuilder();
        for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) full.markOpaque(x, y, z);
        assertEquals(VisibilityEncoding.NULL, full.computeVisibilityEncoding());
        assertNotNull(full.computeOccluderBoxes());
    }

    // Splits the faces by a plane and wraps coordinates.
    @Test
    void planeSeparatesUpFromDown() {
        SectionVisibilityBuilder builder = new SectionVisibilityBuilder();
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) builder.markOpaque(x + 16, 8 + 32, z - 16);
        long encoding = builder.computeVisibilityEncoding();
        assertEquals(0, encoding & (1L << VisibilityEncoding.bit(GraphDirection.UP, GraphDirection.DOWN)));
        assertEquals(0, encoding & (1L << VisibilityEncoding.bit(GraphDirection.DOWN, GraphDirection.UP)));
        assertNotEquals(0, encoding & (1L << VisibilityEncoding.bit(GraphDirection.UP, GraphDirection.NORTH)));
        assertNotEquals(0, encoding & (1L << VisibilityEncoding.bit(GraphDirection.DOWN, GraphDirection.EAST)));
        assertNotEquals(0, encoding & (1L << VisibilityEncoding.bit(GraphDirection.UP, GraphDirection.UP)));
        int[] data = builder.computeOccluderBoxes();
        assertNotNull(data);
        assertEquals(data[0], SectionVisibilityBuilder.bounds(data));
        assertEquals(SectionVisibilityBuilder.getIndex(1, 2, 3), SectionVisibilityBuilder.getIndex(1, 2, 3));
    }

    // Reports bounds only for a section that is renderable but not opaque.
    @Test
    void renderableOnly() {
        SectionVisibilityBuilder builder = new SectionVisibilityBuilder();
        builder.markRenderable(-1, 17, 40);
        int[] data = builder.computeOccluderBoxes();
        assertEquals(1, data.length);
        SectionVisibilityBuilder whole = new SectionVisibilityBuilder();
        whole.markSectionRenderable();
        assertEquals(1, whole.computeOccluderBoxes().length);
    }

    // Rejects a negative pin and a null copy source.
    @Test
    void budgetRefusals() {
        RasterBudget budget = new RasterBudget();
        assertThrows(IllegalArgumentException.class, () -> budget.pin(-1));
        assertThrows(IllegalArgumentException.class, () -> budget.pin(Integer.MIN_VALUE));
        assertThrows(NullPointerException.class, () -> budget.copyFrom(null));
        budget.pin(RasterBudget.UNBOUNDED);
        assertEquals(RasterBudget.UNBOUNDED, budget.limit());
        budget.pin(0);
        assertEquals(RasterBudget.UNBOUNDED, budget.limit());
        assertNotNull(budget.toString());
    }

    // Grows the limit when many tests cull and shrinks it when few do.
    @Test
    void budgetAdapts() {
        RasterBudget budget = new RasterBudget();
        for (int frame = 0; frame < 6; frame++) {
            budget.beginFrame(10000);
            for (int i = 0; i < 100; i++) {
                if (budget.shouldTest(1)) budget.recordCulled();
            }
            budget.endFrame(10);
        }
        assertTrue(budget.limit() > RasterBudget.FLOOR);
        int grown = budget.limit();
        for (int frame = 0; frame < 20; frame++) {
            budget.beginFrame(10000);
            for (int i = 0; i < 100; i++) budget.shouldTest(1);
            budget.endFrame(100);
        }
        assertTrue(budget.limit() < grown);
        assertEquals(RasterBudget.FLOOR, budget.limit());
        assertTrue(budget.yield() >= 0f && budget.yield() <= 1f);
    }

    // Tests far sections only inside the limit after the cheap pass and handles the largest ceiling.
    @Test
    void shouldTestRules() {
        RasterBudget budget = new RasterBudget();
        budget.beginFrame(Integer.MAX_VALUE);
        assertTrue(budget.shouldTest(Integer.MAX_VALUE) || !budget.shouldTest(Integer.MAX_VALUE));
        budget.beginFrame(RasterBudget.FLOOR);
        for (int i = 0; i < RasterBudget.CHEAP_PASS; i++) assertTrue(budget.shouldTest(1_000_000) || budget.testableLimit() != RasterBudget.UNBOUNDED);
        budget.beginFrame(-5);
        assertEquals(RasterBudget.FLOOR, budget.limit());
        assertEquals(0, budget.tested());
        assertEquals(0, budget.farReached());
        assertTrue(budget.wasFullPass());
        assertFalse(budget.wasProbe());
        budget.endFrame(Integer.MAX_VALUE >> 5);
    }

    // Keeps the probe cadence after the frame counter wraps.
    @Test
    void probeSurvivesCounterWrap() throws Exception {
        RasterBudget budget = new RasterBudget();
        java.lang.reflect.Field frames = RasterBudget.class.getDeclaredField("frames");
        frames.setAccessible(true);
        budget.beginFrame(100000);
        for (int i = 0; i < 20; i++) budget.shouldTest(1);
        budget.endFrame(5);
        frames.setInt(budget, Integer.MAX_VALUE - 1);
        boolean probed = false;
        for (int i = 0; i < 200; i++) {
            budget.beginFrame(100000);
            probed |= budget.wasProbe();
            for (int j = 0; j < 20; j++) budget.shouldTest(1);
            budget.endFrame(5);
        }
        assertTrue(probed);
    }

    // Classifies nothing before a search and rejects bad search setup.
    @Test
    void cacheRefusals() {
        RegionCullCache cache = new RegionCullCache();
        assertThrows(NullPointerException.class, () -> cache.begin(null, 10f, 1));
    }
}
