package com.bdmajora.impetus.engine.impl.render.mesh.region;

import com.bdmajora.impetus.engine.impl.common.util.NativeBuffer;
import com.bdmajora.impetus.engine.impl.render.mesh.MeshShaderSupport;
import com.bdmajora.impetus.engine.impl.render.mesh.renderers.TerrainRasterizer;
import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;

import static org.junit.jupiter.api.Assertions.*;

// Tests region history and geometry bounds at their limits.
class MeshRegionEdgeTest {
    // Reports regions after enough samples and by oldest sighting.
    @Test
    void visibilityHistory() {
        RegionVisibilityTracker tracker = new RegionVisibilityTracker(4);
        assertEquals(-1, tracker.leastRecentlySeen(4, id -> true));
        long address = MemoryUtil.nmemAlloc(2);
        try {
            MemoryUtil.memPutByte(address, (byte) 1);
            MemoryUtil.memPutByte(address + 1, (byte) 0);
            for (int i = 0; i <= RegionVisibilityTracker.MIN_SAMPLES; i++) {
                tracker.record(new short[]{0, 1}, 2, address);
            }
        } finally {
            MemoryUtil.nmemFree(address);
        }
        assertEquals(1, tracker.leastRecentlySeen(4, id -> true));
        assertEquals(-1, tracker.leastRecentlySeen(4, id -> false));
        assertEquals(-1, tracker.leastRecentlySeen(1, id -> id == 1));
        tracker.reset(1);
        assertEquals(-1, tracker.leastRecentlySeen(4, id -> id == 1));
        tracker.clear();
        assertEquals(-1, tracker.leastRecentlySeen(4, id -> true));
        assertThrows(IllegalArgumentException.class, () -> new RegionVisibilityTracker(-1));
        new RegionVisibilityTracker(0).record(new short[0], 0, 0);
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> tracker.reset(4));
    }

    // Measures bounds of packed geometry and rejects bad input.
    @Test
    void geometryBounds() {
        NativeBuffer buffer = new NativeBuffer(4 * 16);
        for (int i = 0; i < 4; i++) {
            long ptr = MemoryUtil.memAddress(buffer.getDirectBuffer()) + i * 16L;
            MemoryUtil.memPutShort(ptr, (short) com.bdmajora.impetus.engine.impl.render.chunk.vertex.format.impl.MeshChunkVertex.encodePosition(2 + (i & 1)));
            MemoryUtil.memPutShort(ptr + 2, (short) com.bdmajora.impetus.engine.impl.render.chunk.vertex.format.impl.MeshChunkVertex.encodePosition(5));
            MemoryUtil.memPutShort(ptr + 4, (short) com.bdmajora.impetus.engine.impl.render.chunk.vertex.format.impl.MeshChunkVertex.encodePosition(7));
        }
        SectionGeometry geometry = SectionGeometry.bounded(1, buffer, new short[7], (short) 0, 16);
        assertEquals(1, geometry.quadCount());
        assertEquals(1, geometry.sizeX());
        assertEquals(0, geometry.sizeY());
        assertEquals(5, geometry.minY());
        assertThrows(IllegalArgumentException.class, () -> SectionGeometry.bounded(0, buffer, new short[7], (short) 0, 16));
        assertThrows(IllegalArgumentException.class, () -> SectionGeometry.bounded(-1, buffer, new short[7], (short) 0, 16));
        assertThrows(IllegalArgumentException.class, () -> SectionGeometry.bounded(2, buffer, new short[7], (short) 0, 16));
        assertThrows(IllegalArgumentException.class, () -> SectionGeometry.bounded(1, buffer, new short[7], (short) 0, 0));
        assertThrows(IllegalArgumentException.class, () -> SectionGeometry.bounded(Integer.MAX_VALUE, buffer, new short[7], (short) 0, 16));
        assertThrows(NullPointerException.class, () -> SectionGeometry.bounded(1, null, new short[7], (short) 0, 16));
        geometry.delete();
    }

    // Rejects a negative draw count and a missing reason.
    @Test
    void smallRefusals() {
        assertThrows(IllegalArgumentException.class, () -> TerrainRasterizer.drawIndirect(-1, 0L));
        assertThrows(NullPointerException.class, () -> MeshShaderSupport.markBroken(null));
    }
}
