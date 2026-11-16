package com.bdmajora.impetus.engine.impl.render.mesh.region;

import com.bdmajora.impetus.engine.impl.render.mesh.util.UploadStream;
import com.bdmajora.testing.TestGl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

// Tests the capacity and id handling of the region store.
class RegionStoreEdgeTest {
    // Makes the mocked GL capable of the mesh path.
    @BeforeEach
    void capable() {
        TestGl.meshCapable();
    }

    // Rejects bad construction arguments.
    @Test
    void constructorRefusals() {
        UploadStream stream = new UploadStream(1 << 20);
        assertThrows(IllegalArgumentException.class, () -> new MeshRegionStore(0, stream));
        assertThrows(IllegalArgumentException.class, () -> new MeshRegionStore(-1, stream));
        assertThrows(NullPointerException.class, () -> new MeshRegionStore(4, null));
        MeshRegionStore store = new MeshRegionStore(4, stream);
        assertThrows(NullPointerException.class, () -> store.setUploadListener(null));
        store.delete();
        store.delete();
    }

    // Reports ids outside the table as missing and rejects updates of missing sections.
    @Test
    void missingIds() {
        MeshRegionStore store = new MeshRegionStore(2, new UploadStream(1 << 20));
        assertFalse(store.regionExists(-1));
        assertFalse(store.regionExists(2));
        assertFalse(store.regionExists(Integer.MAX_VALUE));
        assertFalse(store.regionExists(0));
        assertThrows(IllegalStateException.class, () -> store.beginSectionUpdate(0));
        assertThrows(IllegalStateException.class, () -> store.beginSectionUpdate(Integer.MAX_VALUE));
        assertThrows(IllegalStateException.class, () -> store.getSectionIndex(5 << 8));
        store.removeSection(0);
        store.removeSection(-1 >>> 1);
        store.commit();
        store.delete();
    }

    // Stops at the region capacity without leaving a phantom region.
    @Test
    void capacityIsEnforced() {
        MeshRegionStore store = new MeshRegionStore(2, new UploadStream(1 << 20));
        int a = store.allocateSection(0, 0, 0);
        store.allocateSection(8, 0, 0);
        assertEquals(2, store.getRegionCount());
        assertThrows(IllegalStateException.class, () -> store.allocateSection(16, 0, 0));
        assertEquals(2, store.getRegionCount());
        assertFalse(store.hasRegionFor(16, 0, 0));
        assertThrows(IllegalStateException.class, () -> store.allocateSection(0, 0, 0));
        store.removeSection(a);
        assertEquals(1, store.getRegionCount());
        int c = store.allocateSection(16, 0, 0);
        assertEquals(a >>> 8, c >>> 8);
        assertEquals(2, store.getMaxRegionIndex());
        store.commit();
        store.delete();
    }

    // Compacts sections so live ones stay at the front.
    @Test
    void sectionCompaction() {
        MeshRegionStore store = new MeshRegionStore(1, new UploadStream(1 << 20));
        int s0 = store.allocateSection(0, 0, 0);
        int s1 = store.allocateSection(1, 0, 0);
        int s2 = store.allocateSection(2, 0, 0);
        assertEquals(3, store.getSectionCount(0));
        store.removeSection(s0);
        assertEquals(2, store.getSectionCount(0));
        assertEquals(0, store.getSectionIndex(s2));
        assertEquals(1, store.getSectionIndex(s1));
        store.removeSection(s0);
        store.removeSection(s1);
        store.removeSection(s2);
        assertFalse(store.regionExists(0));
        assertEquals(0, store.getMaxRegionIndex());
        store.commit();
        store.delete();
    }

    // Frees a partial native allocation and leaves no phantom region when a region cannot be created.
    @Test
    void failedRegionAllocationLeavesNoRegion() {
        MeshRegionStore store = new MeshRegionStore(2, new UploadStream(1 << 20));
        java.util.List<Long> freed = new java.util.ArrayList<>();
        int[] calls = {0};
        try (var memory = org.mockito.Mockito.mockStatic(org.lwjgl.system.MemoryUtil.class, org.mockito.Mockito.CALLS_REAL_METHODS)) {
            memory.when(() -> org.lwjgl.system.MemoryUtil.nmemCalloc(org.mockito.Mockito.anyLong(), org.mockito.Mockito.anyLong())).thenAnswer(inv -> {
                calls[0]++;
                return calls[0] == 2 ? 0L : inv.callRealMethod();
            });
            memory.when(() -> org.lwjgl.system.MemoryUtil.nmemFree(org.mockito.Mockito.anyLong())).thenAnswer(inv -> {
                freed.add(inv.getArgument(0));
                return inv.callRealMethod();
            });
            assertThrows(OutOfMemoryError.class, () -> store.allocateSection(0, 0, 0));
        }
        assertEquals(1, freed.size());
        assertNotEquals(0L, freed.get(0));
        assertEquals(0, store.getRegionCount());
        assertFalse(store.hasRegionFor(0, 0, 0));
        int section = store.allocateSection(0, 0, 0);
        assertEquals(0, section >>> 8);
        store.allocateSection(8, 0, 0);
        assertEquals(2, store.getRegionCount());
        store.delete();
    }
}
