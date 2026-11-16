package com.bdmajora.impetus.engine.impl.render.mesh;

import com.bdmajora.impetus.engine.impl.gui.ImpetusGameOptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

// Tests region slot and budget calculations at their limits.
class MeshRendererLimitsTest {
    // Config with a render distance and keep distance.
    private static MeshTerrainConfig config(int renderDistance, int keep) {
        return new MeshTerrainConfig(true, ImpetusGameOptions.MeshTranslucencySorting.NONE, ImpetusGameOptions.MeshStatistics.NONE, false, 1024, keep, renderDistance);
    }

    // Clamps the slot count between the limits and survives huge distances.
    @Test
    void regionSlots() {
        assertEquals(MeshTerrainRenderer.MIN_REGIONS, MeshTerrainRenderer.computeMaxRegions(config(0, 0), 0));
        assertEquals(MeshTerrainRenderer.MIN_REGIONS, MeshTerrainRenderer.computeMaxRegions(config(0, 0), -5));
        assertEquals(MeshTerrainRenderer.MAX_REGIONS, MeshTerrainRenderer.computeMaxRegions(config(Integer.MAX_VALUE, 0), 4));
        assertEquals(MeshTerrainRenderer.MAX_REGIONS, MeshTerrainRenderer.computeMaxRegions(config(Integer.MAX_VALUE, Integer.MAX_VALUE), Integer.MAX_VALUE));
        assertEquals(43560, MeshTerrainRenderer.computeMaxRegions(config(0, MeshTerrainConfig.MAX_KEEP_DISTANCE), 8));
        int mid = MeshTerrainRenderer.computeMaxRegions(config(12, 0), 4);
        assertTrue(mid >= MeshTerrainRenderer.MIN_REGIONS && mid <= MeshTerrainRenderer.MAX_REGIONS);
        assertTrue(MeshTerrainRenderer.computeMaxRegions(config(32, 0), 4) >= mid);
    }

    // Uses the manual budget when automatic memory is off.
    @Test
    void manualBudget() {
        assertEquals(1024, MeshTerrainRenderer.computeBudgetMB(config(8, 0), true, 0));
        assertEquals(1024, MeshTerrainRenderer.computeBudgetMB(config(8, 0), false, Long.MAX_VALUE));
    }

    // Leaves the far plane alone without a live backend.
    @Test
    void farPlane() {
        assertFalse(MeshTerrainRenderer.isActive());
        assertEquals(256f, MeshTerrainRenderer.extendFarPlane(256f));
        assertEquals(0f, MeshTerrainRenderer.extendFarPlane(0f));
        assertTrue(Float.isNaN(MeshTerrainRenderer.extendFarPlane(Float.NaN)));
    }

    // Rejects a region count that does not fit a region id and a missing config.
    @Test
    void pipelineArgumentRefusals() {
        MeshTerrainConfig config = config(8, 0);
        for (int bad : new int[]{0, -1, 65536, Integer.MAX_VALUE, Integer.MIN_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> new MeshRenderPipeline(config, bad, 1024, 1024, 1 << 20));
        }
        assertThrows(NullPointerException.class, () -> new MeshRenderPipeline(null, 10, 1024, 1024, 1 << 20));
        assertThrows(NullPointerException.class, () -> new MeshTerrainRenderer(null, 4));
    }
}
