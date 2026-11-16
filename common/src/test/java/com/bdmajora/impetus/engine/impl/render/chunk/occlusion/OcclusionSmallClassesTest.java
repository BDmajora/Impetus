package com.bdmajora.impetus.engine.impl.render.chunk.occlusion;

import com.bdmajora.impetus.engine.impl.render.chunk.occlusion.geometry.AreaFinder;
import com.bdmajora.impetus.engine.impl.render.chunk.occlusion.geometry.BoxFinder;
import com.bdmajora.impetus.engine.impl.render.chunk.occlusion.geometry.OccluderBoxes;
import grondag.bitraster.PackedBox;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

// Tests direction tables and bounds and occluder box input checks.
class OcclusionSmallClassesTest {
    // Pairs every direction with its opposite and unit steps.
    @Test
    void directions() {
        for (int d = 0; d < GraphDirection.COUNT; d++) {
            int o = GraphDirection.opposite(d);
            assertEquals(d, GraphDirection.opposite(o));
            assertEquals(-GraphDirection.x(d), GraphDirection.x(o));
            assertEquals(-GraphDirection.y(d), GraphDirection.y(o));
            assertEquals(-GraphDirection.z(d), GraphDirection.z(o));
            assertEquals(1, Math.abs(GraphDirection.x(d)) + Math.abs(GraphDirection.y(d)) + Math.abs(GraphDirection.z(d)));
        }
        assertEquals(GraphDirection.COUNT, GraphDirection.LETTERS.length);
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> GraphDirection.opposite(6));
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> GraphDirection.x(-1));
    }

    // Builds sets and rejects numbers that are not directions.
    @Test
    void directionSets() {
        int all = 0;
        for (int d = 0; d < GraphDirection.COUNT; d++) {
            assertTrue(GraphDirectionSet.contains(GraphDirectionSet.of(d), d));
            assertFalse(GraphDirectionSet.contains(GraphDirectionSet.NONE, d));
            assertTrue(GraphDirectionSet.contains(GraphDirectionSet.ALL, d));
            all |= GraphDirectionSet.of(d);
        }
        assertEquals(GraphDirectionSet.ALL, all);
        for (int bad : new int[]{-1, 6, 32, 33, Integer.MAX_VALUE, Integer.MIN_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> GraphDirectionSet.of(bad));
            assertThrows(IllegalArgumentException.class, () -> GraphDirectionSet.contains(GraphDirectionSet.ALL, bad));
        }
    }

    // Reports the full box when empty and widens marked cells by one.
    @Test
    void renderableBounds() {
        RenderableBounds bounds = new RenderableBounds();
        assertTrue(bounds.isEmpty());
        assertEquals(PackedBox.FULL_BOX, bounds.packed());
        bounds.mark(0, 0, 0);
        assertFalse(bounds.isEmpty());
        int box = bounds.packed();
        assertEquals(0, PackedBox.x0(box));
        assertEquals(2, PackedBox.x1(box));
        bounds.mark(15, 15, 15);
        box = bounds.packed();
        assertEquals(16, PackedBox.x1(box));
        assertEquals(0, PackedBox.y0(box));
        RenderableBounds middle = new RenderableBounds();
        middle.mark(5, 6, 7);
        middle.mark(5, 6, 7);
        box = middle.packed();
        assertEquals(4, PackedBox.x0(box));
        assertEquals(7, PackedBox.x1(box));
        assertEquals(5, PackedBox.y0(box));
        assertEquals(6, PackedBox.z0(box));
    }

    // Rejects null and short input and finds nothing in empty input.
    @Test
    void occluderBoxInputs() {
        assertThrows(NullPointerException.class, () -> OccluderBoxes.from(null));
        assertThrows(IndexOutOfBoundsException.class, () -> OccluderBoxes.from(new long[BoxFinder.INTERIOR_CACHE_WORDS - 1]));
        assertThrows(IndexOutOfBoundsException.class, () -> OccluderBoxes.from(new long[0]));
        assertNull(OccluderBoxes.from(new long[BoxFinder.INTERIOR_CACHE_WORDS]));
        long[] solid = new long[BoxFinder.INTERIOR_CACHE_WORDS];
        Arrays.fill(solid, -1L);
        int[] boxes = OccluderBoxes.from(solid);
        assertNotNull(boxes);
        assertTrue(boxes.length > 0);
        assertNull(OccluderBoxes.from(new long[BoxFinder.INTERIOR_CACHE_WORDS]));
        BoxFinder finder = new BoxFinder(new AreaFinder());
        assertThrows(IndexOutOfBoundsException.class, () -> finder.findBoxes(new long[BoxFinder.INTERIOR_CACHE_WORDS], 1));
        assertThrows(IndexOutOfBoundsException.class, () -> finder.findBoxes(new long[BoxFinder.INTERIOR_CACHE_WORDS], -1));
        assertThrows(NullPointerException.class, () -> finder.findBoxes(null, 0));
        finder.findBoxes(new long[BoxFinder.INTERIOR_CACHE_WORDS * 2], BoxFinder.INTERIOR_CACHE_WORDS);
    }

    // Names the search modes.
    @Test
    void asyncModes() {
        assertEquals(3, AsyncOcclusionMode.values().length);
        assertSame(AsyncOcclusionMode.NONE, AsyncOcclusionMode.valueOf("NONE"));
    }
}
