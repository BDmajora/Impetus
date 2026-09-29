package com.bdmajora.impetus.engine.impl.render.chunk;

import com.bdmajora.impetus.engine.impl.render.chunk.terrain.TerrainRenderPass;
import com.bdmajora.impetus.engine.impl.render.chunk.terrain.material.Material;
import com.bdmajora.testing.Passes;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ChunkBasicsTest {
    @Test
    void localSectionIndexRoundTrips() {
        for (int x = 0; x < 8; x++) {
            for (int y = 0; y < 4; y++) {
                for (int z = 0; z < 8; z++) {
                    int idx = LocalSectionIndex.pack(x, y, z);
                    assertEquals(x, LocalSectionIndex.unpackX(idx));
                    assertEquals(y, LocalSectionIndex.unpackY(idx));
                    assertEquals(z, LocalSectionIndex.unpackZ(idx));
                }
            }
        }
        new LocalSectionIndex();
    }

    @Test
    void updateTypesPromote() {
        assertNull(ChunkUpdateType.getPromotionUpdateType(ChunkUpdateType.REBUILD, ChunkUpdateType.REBUILD));
        assertEquals(ChunkUpdateType.REBUILD, ChunkUpdateType.getPromotionUpdateType(null, ChunkUpdateType.REBUILD));
        assertEquals(ChunkUpdateType.REBUILD, ChunkUpdateType.getPromotionUpdateType(ChunkUpdateType.SORT, ChunkUpdateType.REBUILD));
        assertEquals(ChunkUpdateType.IMPORTANT_REBUILD, ChunkUpdateType.getPromotionUpdateType(ChunkUpdateType.REBUILD, ChunkUpdateType.IMPORTANT_REBUILD));
        assertEquals(ChunkUpdateType.IMPORTANT_REBUILD, ChunkUpdateType.getPromotionUpdateType(ChunkUpdateType.IMPORTANT_SORT, ChunkUpdateType.REBUILD));
        assertEquals(ChunkUpdateType.IMPORTANT_REBUILD, ChunkUpdateType.getPromotionUpdateType(ChunkUpdateType.REBUILD, ChunkUpdateType.IMPORTANT_SORT));
        assertNull(ChunkUpdateType.getPromotionUpdateType(ChunkUpdateType.IMPORTANT_REBUILD, ChunkUpdateType.REBUILD));
        assertNull(ChunkUpdateType.getPromotionUpdateType(ChunkUpdateType.REBUILD, ChunkUpdateType.SORT));
        assertTrue(ChunkUpdateType.IMPORTANT_SORT.isImportant());
        assertFalse(ChunkUpdateType.REBUILD.isImportant());
        assertTrue(ChunkUpdateType.SORT.isSort());
        assertFalse(ChunkUpdateType.INITIAL_BUILD.isSort());
        assertEquals(5, ChunkUpdateType.VALUES.length);
    }

    @Test
    void renderPassConfigurationMapsTypesToMaterials() {
        RenderPassConfiguration<String> config = new RenderPassConfiguration<>(
                Map.of("solid", Passes.SOLID_MATERIAL, "cutout", Passes.CUTOUT_MATERIAL),
                Map.of("solid", List.of(Passes.SOLID), "cutout", List.of(Passes.SOLID, Passes.CUTOUT)),
                Passes.SOLID_MATERIAL, Passes.CUTOUT_MATERIAL, Passes.TRANSLUCENT_MATERIAL);
        assertSame(Passes.CUTOUT_MATERIAL, config.getMaterialForRenderType("cutout"));
        assertThrows(IllegalArgumentException.class, () -> config.getMaterialForRenderType("nope"));
        assertThrows(NullPointerException.class, () -> config.getMaterialForRenderType(null));
        List<TerrainRenderPass> passes = config.getAllKnownRenderPasses().toList();
        assertEquals(2, passes.size());
        assertSame(Passes.SOLID_MATERIAL, config.defaultSolidMaterial());
        assertSame(Passes.CUTOUT_MATERIAL, config.defaultCutoutMippedMaterial());
        assertSame(Passes.TRANSLUCENT_MATERIAL, config.defaultTranslucentMaterial());
        Material m = config.chunkRenderTypeToMaterialMap().get("solid");
        assertEquals(Passes.SOLID_MATERIAL, m);
    }
}
