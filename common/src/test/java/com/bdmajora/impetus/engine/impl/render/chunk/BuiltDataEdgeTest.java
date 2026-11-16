package com.bdmajora.impetus.engine.impl.render.chunk;

import com.bdmajora.impetus.engine.impl.common.util.NativeBuffer;
import com.bdmajora.impetus.engine.impl.render.chunk.data.BuiltRenderSectionData;
import com.bdmajora.impetus.engine.impl.render.chunk.data.BuiltSectionMeshParts;
import com.bdmajora.impetus.engine.impl.render.chunk.data.MinecraftBuiltRenderSectionData;
import com.bdmajora.impetus.engine.impl.render.chunk.lists.RenderVisualsService;
import com.bdmajora.impetus.engine.impl.render.chunk.lists.SortedRenderLists;
import com.bdmajora.impetus.engine.impl.render.chunk.metrics.RenderSectionMetricsTracker;
import com.bdmajora.impetus.engine.impl.render.chunk.multidraw.CachedBatch;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

// Tests section data and cached batches and the build time tracker.
class BuiltDataEdgeTest {
    // Compares section data by contents and builds the visual mask.
    @Test
    void sectionDataEquality() {
        BuiltRenderSectionData a = new BuiltRenderSectionData();
        BuiltRenderSectionData b = new BuiltRenderSectionData();
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertEquals(0, a.getVisualBitmaskForSection());
        a.hasBlockGeometry = true;
        assertNotEquals(a, b);
        assertEquals(1 << RenderVisualsService.HAS_BLOCK_GEOMETRY, a.getVisualBitmaskForSection());
        b.hasBlockGeometry = true;
        a.occluderBoxes = new int[]{1, 2};
        assertNotEquals(a, b);
        b.occluderBoxes = new int[]{1, 2};
        assertEquals(a, b);
        a.visibilityData = Long.MIN_VALUE;
        assertNotEquals(a, b);
        assertNotEquals(a, null);
        assertNotEquals(a, "x");
        a.bake();
    }

    // Reports sprites and block entities in the mask and freezes lists when baked.
    @Test
    void minecraftDataMasksAndBake() {
        MinecraftBuiltRenderSectionData<String, String> data = new MinecraftBuiltRenderSectionData<>();
        assertEquals(0, data.getVisualBitmaskForSection());
        data.animatedSprites.add("s");
        data.culledBlockEntities.add("e");
        data.bake();
        assertEquals((1 << RenderVisualsService.HAS_SPRITES) | (1 << RenderVisualsService.HAS_BLOCK_ENTITIES), data.getVisualBitmaskForSection());
        assertThrows(UnsupportedOperationException.class, () -> data.animatedSprites.add("t"));
        assertThrows(UnsupportedOperationException.class, () -> data.culledBlockEntities.add("t"));
        assertThrows(UnsupportedOperationException.class, () -> data.globalBlockEntities.add("t"));
        data.bake();
        MinecraftBuiltRenderSectionData<String, String> other = new MinecraftBuiltRenderSectionData<>();
        other.animatedSprites.add("s");
        other.culledBlockEntities.add("e");
        other.bake();
        assertEquals(data, other);
        assertEquals(data.hashCode(), other.hashCode());
        other.globalBlockEntities = List.of("g");
        assertNotEquals(data, other);
        assertEquals(1 << RenderVisualsService.HAS_BLOCK_ENTITIES, onlyGlobal().getVisualBitmaskForSection());
    }

    // Creates data with only a global block entity.
    private static MinecraftBuiltRenderSectionData<String, String> onlyGlobal() {
        MinecraftBuiltRenderSectionData<String, String> data = new MinecraftBuiltRenderSectionData<>();
        data.globalBlockEntities.add("g");
        return data;
    }

    // Visits only sections whose data is ours.
    @Test
    void globalVisit() {
        RenderSection ours = Mockito.mock(RenderSection.class);
        RenderSection other = Mockito.mock(RenderSection.class);
        MinecraftBuiltRenderSectionData<String, String> data = new MinecraftBuiltRenderSectionData<>();
        Mockito.when(ours.getBuiltContext()).thenReturn(data);
        Mockito.when(other.getBuiltContext()).thenReturn(new BuiltRenderSectionData());
        int[] visits = {0};
        MinecraftBuiltRenderSectionData.forEachGlobalSectionData(List.of(ours, other), d -> visits[0]++);
        assertEquals(1, visits[0]);
        MinecraftBuiltRenderSectionData.forEachGlobalSectionData(List.of(), d -> visits[0]++);
        assertEquals(1, visits[0]);
    }

    // Rejects null buffers and frees the index buffer even when the vertex buffer fails.
    @Test
    void meshPartsFree() {
        NativeBuffer vertices = new NativeBuffer(16);
        NativeBuffer indices = new NativeBuffer(16);
        BuiltSectionMeshParts parts = new BuiltSectionMeshParts(vertices, indices, null, Map.of());
        parts.free();
        assertThrows(NullPointerException.class, () -> new BuiltSectionMeshParts(null, null, null, Map.of()));
        assertThrows(NullPointerException.class, () -> new BuiltSectionMeshParts(new NativeBuffer(8), null, null, null));
        BuiltSectionMeshParts noIndex = new BuiltSectionMeshParts(new NativeBuffer(8), null, null, Map.of());
        noIndex.free();
    }

    // Validates the section count and the camera cell.
    @Test
    void cachedBatchValidity() {
        byte[] sections = {1, 2, 3, 4};
        CachedBatch batch = new CachedBatch(null, null, sections, 3, 0, 10, -5, 5, 0, 1);
        assertTrue(batch.isValidFor(sections, 3, 0, 0, 0));
        assertTrue(batch.isValidFor(new byte[]{1, 2, 3, 9}, 3, 9, -5, 0));
        assertFalse(batch.isValidFor(sections, 3, 10, 0, 0));
        assertFalse(batch.isValidFor(sections, 3, -1, 0, 0));
        assertFalse(batch.isValidFor(sections, 3, 0, 5, 0));
        assertFalse(batch.isValidFor(sections, 3, 0, 0, 1));
        assertFalse(batch.isValidFor(sections, 4, 0, 0, 0));
        assertFalse(batch.isValidFor(sections, 2, 0, 0, 0));
        assertFalse(batch.isValidFor(new byte[]{1, 2, 4, 0}, 3, 0, 0, 0));
        assertFalse(batch.isValidFor(sections, -1, 0, 0, 0));
        assertNull(batch.getBatch());
        assertNull(batch.getTessellation());
        batch.delete();
        sections[0] = 9;
        assertTrue(batch.isValidFor(new byte[]{1, 2, 3}, 3, 0, 0, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> new CachedBatch(null, null, new byte[2], 3, 0, 1, 0, 1, 0, 1));
        assertThrows(IndexOutOfBoundsException.class, () -> new CachedBatch(null, null, new byte[2], -1, 0, 1, 0, 1, 0, 1));
        assertThrows(NullPointerException.class, () -> new CachedBatch(null, null, null, 0, 0, 1, 0, 1, 0, 1));
        assertTrue(new CachedBatch(null, null, new byte[0], 0, 0, 1, 0, 1, 0, 1).isValidFor(new byte[0], 0, 0, 0, 0));
    }

    // Reports no regions for the shared empty lists.
    @Test
    void emptySortedLists() {
        SortedRenderLists empty = SortedRenderLists.empty();
        assertSame(empty, SortedRenderLists.empty());
        assertEquals(0, empty.getNumRegions());
        assertFalse(empty.hasSortedPass());
        assertTrue(empty.getPasses().isEmpty());
        assertFalse(empty.iterator(true).hasNext());
        assertFalse(empty.iterator().hasNext());
        assertFalse(empty.hasPass(null));
    }

    // Keeps only the slowest sections and updates a section that builds again.
    @Test
    void slowestSections() {
        Map<RenderSection, Long> durations = new HashMap<>();
        RenderSectionMetricsTracker tracker = new RenderSectionMetricsTracker();
        RenderSection[] sections = new RenderSection[7];
        for (int i = 0; i < sections.length; i++) {
            RenderSection section = Mockito.mock(RenderSection.class);
            durations.put(section, 0L);
            Mockito.when(section.getLastBuildDurationNanos()).thenAnswer(inv -> durations.get(section));
            Mockito.doAnswer(inv -> durations.put(section, inv.getArgument(0))).when(section).setLastBuildDurationNanos(Mockito.anyLong());
            sections[i] = section;
        }
        for (int i = 0; i < 7; i++) {
            tracker.updateSectionBuildDuration(sections[i], (i + 1) * 100L);
        }
        assertEquals(5, tracker.getSlowestSections().size());
        assertFalse(tracker.getSlowestSections().contains(sections[0]));
        assertFalse(tracker.getSlowestSections().contains(sections[1]));
        tracker.updateSectionBuildDuration(sections[6], 1);
        assertEquals(5, tracker.getSlowestSections().size());
        tracker.updateSectionBuildDuration(sections[0], Long.MAX_VALUE);
        assertTrue(tracker.getSlowestSections().contains(sections[0]));
        tracker.removeSection(sections[0]);
        tracker.removeSection(sections[0]);
        assertFalse(tracker.getSlowestSections().contains(sections[0]));
        assertThrows(UnsupportedOperationException.class, () -> tracker.getSlowestSections().clear());
    }
}
