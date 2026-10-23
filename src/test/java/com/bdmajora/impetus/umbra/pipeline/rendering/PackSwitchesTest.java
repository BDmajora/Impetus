package com.bdmajora.impetus.umbra.pipeline.rendering;

import com.bdmajora.impetus.umbra.material.WorldRenderingSettings;
import com.bdmajora.impetus.umbra.shaderpack.ShaderPack;
import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Statics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PackSwitchesTest {
    private static final String VERTEX = "#version 120\nvoid main() { gl_Position = ftransform(); }";

    @BeforeAll
    static void bootstrap() {
        Mc.bootstrap();
    }

    @AfterEach
    void vanilla() {
        PackSwitches.resetWorldSettings();
    }

    private static PackSwitches of(String properties, String... programs) {
        String[] all = new String[programs.length + 2];
        all[0] = "/shaders.properties";
        all[1] = properties;
        System.arraycopy(programs, 0, all, 2, programs.length);
        return new PackSwitches(PackSourcesTest.pack(all));
    }

    @Test
    void everySwitchDefaultsAsUmbraDoes() {
        PackSwitches defaults = of("");
        assertFalse(defaults.prepareBeforeShadow);
        assertFalse(defaults.allowConcurrentCompute);
        assertFalse(defaults.rainDepth);
        assertFalse(defaults.beaconBeamDepth);
        assertTrue(defaults.frustumCulling);
        assertTrue(defaults.occlusionCulling);
        assertFalse(defaults.skipAllRendering);
        assertFalse(defaults.separateEntityDraws);
        assertEquals("mixed", defaults.particleOrdering);
        for (int layer = 0; layer < 4; layer++) {
            assertTrue(defaults.cullsBackFaces(layer));
        }
    }

    @Test
    void everySwitchFollowsItsDirective() {
        PackSwitches set = of(String.join("\n", "prepareBeforeShadow = true", "allowConcurrentCompute = true",
                "rain.depth = true", "beacon.beam.depth = true", "frustum.culling = false", "occlusion.culling = false",
                "skipAllRendering = true", "separateEntityDraws = true", "particles.ordering = before",
                "backFace.solid = false", "backFace.cutoutMipped = false", "backFace.cutout = true", "backFace.translucent = false"));
        assertTrue(set.prepareBeforeShadow);
        assertTrue(set.allowConcurrentCompute);
        assertTrue(set.rainDepth);
        assertTrue(set.beaconBeamDepth);
        assertFalse(set.frustumCulling);
        assertFalse(set.occlusionCulling);
        assertTrue(set.skipAllRendering);
        assertTrue(set.separateEntityDraws);
        assertEquals("before", set.particleOrdering);
        assertFalse(set.cullsBackFaces(0));
        assertFalse(set.cullsBackFaces(1));
        assertTrue(set.cullsBackFaces(2));
        assertFalse(set.cullsBackFaces(3));
        // Outside the four layers vanilla culls
        assertTrue(set.cullsBackFaces(-1));
        assertTrue(set.cullsBackFaces(4));
    }

    @Test
    void particlesGoAfterAnyDeferredChainUnlessEntitiesDrawSeparately() {
        assertEquals("after", of("", "/deferred.vsh", VERTEX, "/deferred.fsh", "void main() {}").particleOrdering);
        // A chain starting at deferred1 is still a deferred chain
        assertEquals("after", of("", "/deferred3.vsh", VERTEX, "/deferred3.fsh", "void main() {}").particleOrdering);
        assertEquals("after", of("", "/deferred2.csh", "void main() {}").particleOrdering);
        assertEquals("mixed", of("separateEntityDraws = true", "/deferred.vsh", VERTEX, "/deferred.fsh", "void main() {}").particleOrdering);
        assertEquals("mixed", of("", "/composite.vsh", VERTEX, "/composite.fsh", "void main() {}").particleOrdering);
        assertEquals("mixed", of("particles.ordering = mixed", "/deferred.vsh", VERTEX, "/deferred.fsh", "void main() {}").particleOrdering);
    }

    @Test
    void theWorldSettingsArePublishedAndResetToVanillasOwnDefaults() {
        ShaderPack pack = PackSourcesTest.pack("/shaders.properties", String.join("\n", "dynamicHandLight = false",
                "separateAo = true", "oldLighting = false", "oldHandLight = false", "voxelizeLightBlocks = true"),
                "/block.properties", "block.10 = stone\n");
        PackSwitches.publishWorldSettings(pack, 12);
        assertEquals(12, WorldRenderingSettings.getVoxelRenderDistanceChunks());
        assertFalse(WorldRenderingSettings.isOldLighting());
        assertTrue(WorldRenderingSettings.shouldDisableDirectionalShading());
        assertFalse(Statics.<Boolean>get(WorldRenderingSettings.class, "dynamicHandLight"));
        assertTrue(Statics.<Boolean>get(WorldRenderingSettings.class, "separateAo"));
        assertFalse(Statics.<Boolean>get(WorldRenderingSettings.class, "oldHandLight"));
        assertTrue(Statics.<Boolean>get(WorldRenderingSettings.class, "voxelizeLightBlocks"));
        assertNotNull(Statics.get(WorldRenderingSettings.class, "blockStateIds"));
        PackSwitches.resetWorldSettings();
        // oldLighting goes back to TRUE: false left vanilla terrain without face shading after shaders were turned off
        assertTrue(WorldRenderingSettings.isOldLighting());
        assertFalse(WorldRenderingSettings.shouldDisableDirectionalShading());
        assertEquals(0, WorldRenderingSettings.getVoxelRenderDistanceChunks());
        assertTrue(Statics.<Boolean>get(WorldRenderingSettings.class, "dynamicHandLight"));
        assertFalse(Statics.<Boolean>get(WorldRenderingSettings.class, "separateAo"));
        assertTrue(Statics.<Boolean>get(WorldRenderingSettings.class, "oldHandLight"));
        assertFalse(Statics.<Boolean>get(WorldRenderingSettings.class, "voxelizeLightBlocks"));
        assertNull(Statics.get(WorldRenderingSettings.class, "blockStateIds"));
        assertNull(Statics.get(WorldRenderingSettings.class, "itemIds"));
        assertNull(Statics.get(WorldRenderingSettings.class, "entityIds"));
        assertNull(Statics.get(WorldRenderingSettings.class, "blockRenderLayers"));
    }

    @Test
    void anUnconfiguredPackPublishesUmbrasDefaults() {
        PackSwitches.publishWorldSettings(PackSourcesTest.pack("/shaders.properties", ""), 8);
        assertTrue(WorldRenderingSettings.isOldLighting());
        assertTrue(Statics.<Boolean>get(WorldRenderingSettings.class, "dynamicHandLight"));
        assertFalse(Statics.<Boolean>get(WorldRenderingSettings.class, "separateAo"));
        assertEquals(8, WorldRenderingSettings.getVoxelRenderDistanceChunks());
    }
}
