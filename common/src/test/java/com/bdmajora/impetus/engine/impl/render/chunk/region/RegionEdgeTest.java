package com.bdmajora.impetus.engine.impl.render.chunk.region;

import com.bdmajora.impetus.engine.impl.gl.arena.staging.StagingBuffer;
import com.bdmajora.impetus.engine.impl.gl.device.CommandList;
import com.bdmajora.impetus.engine.impl.render.chunk.RenderSection;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;

// Tests region geometry and section bookkeeping.
class RegionEdgeTest {
    // Creates a region at region coordinates.
    private static RenderRegion region(int x, int y, int z) {
        return new RenderRegion(x, y, z, 3, Mockito.mock(StagingBuffer.class), 16);
    }

    // Derives chunk and block positions from region coordinates including negative ones.
    @Test
    void positions() {
        RenderRegion region = region(-1, 2, 3);
        assertEquals(-8, region.getChunkX());
        assertEquals(8, region.getChunkY());
        assertEquals(24, region.getChunkZ());
        assertEquals(-128, region.getOriginX());
        assertEquals(128, region.getOriginY());
        assertEquals(384, region.getOriginZ());
        assertEquals(-64, region.getCenterX());
        assertEquals(160, region.getCenterY());
        assertEquals(448, region.getCenterZ());
        assertEquals(3, region.getId());
        assertEquals(RenderRegion.key(-1, 2, 3), RenderRegion.key(-1, 2, 3));
        assertNotEquals(RenderRegion.key(1, 2, 3), RenderRegion.key(3, 2, 1));
    }

    // Adds and removes sections and rejects duplicates and wrong sections.
    @Test
    void sectionBookkeeping() {
        RenderRegion region = region(0, 0, 0);
        assertTrue(region.isEmpty());
        RenderSection a = Mockito.mock(RenderSection.class);
        RenderSection b = Mockito.mock(RenderSection.class);
        Mockito.when(a.getSectionIndex()).thenReturn(5);
        Mockito.when(b.getSectionIndex()).thenReturn(5);
        assertThrows(NullPointerException.class, () -> region.addSection(null));
        assertThrows(NullPointerException.class, () -> region.removeSection(null));
        assertThrows(NullPointerException.class, () -> region.updateSectionLoadTime(null));
        assertThrows(IllegalStateException.class, () -> region.removeSection(a));
        int revision = region.getDataRevision();
        region.addSection(a);
        assertEquals(revision + 1, region.getDataRevision());
        assertFalse(region.isEmpty());
        assertSame(a, region.getSection(5));
        assertThrows(IllegalStateException.class, () -> region.addSection(b));
        assertThrows(IllegalStateException.class, () -> region.removeSection(b));
        region.updateSectionLoadTime(a);
        assertTrue(region.getSectionLoadTimes()[5] != 0);
        assertEquals(region.getSectionLoadTimes()[5], region.getNewestSectionLoadTime());
        region.removeSection(a);
        assertTrue(region.isEmpty());
        assertNull(region.getSection(5));
        assertEquals(0, region.getSectionLoadTimes()[5]);
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> region.getSection(RenderRegion.REGION_SIZE));
        region.onSectionDataChanged();
    }

    // Deletes twice without failing and resets the section count.
    @Test
    void deleteIsRepeatable() {
        RenderRegion region = region(0, 0, 0);
        RenderSection a = Mockito.mock(RenderSection.class);
        Mockito.when(a.getSectionIndex()).thenReturn(1);
        region.addSection(a);
        CommandList list = Mockito.mock(CommandList.class);
        region.createResources(list);
        assertNotNull(region.getResources());
        region.delete(list);
        region.delete(list);
        assertNull(region.getResources());
        assertTrue(region.isEmpty());
        assertNull(region.getSection(1));
        assertTrue(region.getPasses().isEmpty());
        region.update(list);
        region.removeEmptyStorages();
        region.removeMeshes(0);
    }

    // Reports a missing index arena and deletes resources once.
    @Test
    void deviceResources() {
        CommandList list = Mockito.mock(CommandList.class);
        RenderRegion.DeviceResources resources = new RenderRegion.DeviceResources(list, Mockito.mock(StagingBuffer.class), 16);
        assertThrows(IllegalStateException.class, resources::getIndexBuffer);
        assertNull(resources.getIndexArena());
        assertNull(resources.getTessellation(null) );
        assertThrows(NullPointerException.class, () -> resources.updateTessellation(list, null, null));
        assertFalse(resources.deleteIndexArenaIfPossible(list));
        resources.delete(list);
        resources.delete(list);
    }
}
