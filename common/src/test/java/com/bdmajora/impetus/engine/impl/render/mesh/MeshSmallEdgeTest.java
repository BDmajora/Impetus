package com.bdmajora.impetus.engine.impl.render.mesh;

import com.bdmajora.impetus.engine.impl.gui.ImpetusGameOptions;
import com.bdmajora.impetus.engine.impl.render.chunk.ChunkRenderMatrices;
import com.bdmajora.impetus.engine.impl.render.chunk.fog.FogService;
import com.bdmajora.impetus.engine.impl.render.chunk.shader.ChunkFogMode;
import com.bdmajora.impetus.engine.impl.render.chunk.shader.ChunkShaderComponent;
import com.bdmajora.impetus.engine.impl.render.mesh.gl.MeshProgram;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;

// Tests camera and fog and configuration helpers of the mesh backend.
class MeshSmallEdgeTest {
    // Derives the eye from the model view matrix and falls back for a singular one.
    @Test
    void cameraEye() {
        ChunkRenderMatrices translated = new ChunkRenderMatrices(new Matrix4f(), new Matrix4f().translate(-3f, -4f, -5f));
        MeshCamera camera = MeshCamera.of(translated, 100, 64, -100);
        assertEquals(103.0, camera.eyeX(), 1e-4);
        assertEquals(68.0, camera.eyeY(), 1e-4);
        assertEquals(-95.0, camera.eyeZ(), 1e-4);
        MeshCamera singular = MeshCamera.of(new ChunkRenderMatrices(new Matrix4f(), new Matrix4f().zero()), 1, 2, 3);
        assertEquals(1, singular.eyeX());
        assertEquals(3, singular.eyeZ());
        assertThrows(NullPointerException.class, () -> MeshCamera.of(null, 0, 0, 0));
    }

    // Floors section coordinates for negative and huge eye positions.
    @Test
    void cameraSections() {
        assertEquals(-1, MeshCamera.at(-0.5, -16, 0).sectionX());
        assertEquals(-1, MeshCamera.at(-0.5, -16, 0).sectionY());
        assertEquals(0, MeshCamera.at(0, 0, 15.99).sectionZ());
        assertEquals(1, MeshCamera.at(0, 0, 16).sectionZ());
        assertEquals(0, MeshCamera.at(Double.NaN, 0, 0).sectionX());
        assertEquals(Integer.MAX_VALUE >> 4, MeshCamera.at(1e30, 0, 0).sectionX());
        assertEquals(Integer.MIN_VALUE >> 4, MeshCamera.at(-1e30, 0, 0).sectionX());
    }

    // Maps fog modes and rejects a color of the wrong length.
    @Test
    void fogMapping() {
        FogService service = Mockito.mock(FogService.class);
        Mockito.when(service.getFogColor()).thenReturn(new float[]{1, 1, 1, 1});
        int[] expected = {0, 3, 2, 1};
        ChunkFogMode[] modes = {ChunkFogMode.NONE, ChunkFogMode.EXP, ChunkFogMode.EXP2, ChunkFogMode.SMOOTH};
        for (int i = 0; i < modes.length; i++) {
            Mockito.doReturn(modes[i]).when(service).getFogMode();
            assertEquals(expected[i], MeshFog.from(service).mode());
        }
        Mockito.doReturn(Mockito.mock(ChunkShaderComponent.Factory.class)).when(service).getFogMode();
        assertEquals(0, MeshFog.from(service).mode());
        assertThrows(IllegalArgumentException.class, () -> new MeshFog(0, 0, 0, 0, 0, new float[3]));
        assertThrows(IllegalArgumentException.class, () -> new MeshFog(0, 0, 0, 0, 0, null));
        assertEquals(4, MeshFog.NONE.colour().length);
    }

    // Limits negative settings to zero and rejects null parts.
    @Test
    void configFromSettings() {
        ImpetusGameOptions.MeshTerrainSettings settings = new ImpetusGameOptions.MeshTerrainSettings();
        settings.maxGeometryMemory = -5;
        settings.regionKeepDistance = Integer.MIN_VALUE;
        MeshTerrainConfig config = MeshTerrainConfig.from(settings, -3);
        assertEquals(0, config.maxGeometryMemoryMB());
        assertEquals(0, config.regionKeepDistance());
        assertEquals(0, config.renderDistance());
        assertFalse(config.keepsBeyondRenderDistance());
        assertFalse(config.keepsEverything());
        settings.regionKeepDistance = MeshTerrainConfig.MAX_KEEP_DISTANCE;
        MeshTerrainConfig keep = MeshTerrainConfig.from(settings, 8);
        assertTrue(keep.keepsEverything());
        assertTrue(keep.keepsBeyondRenderDistance());
        assertThrows(NullPointerException.class, () -> MeshTerrainConfig.from(null, 1));
        settings.translucencySorting = null;
        assertThrows(NullPointerException.class, () -> MeshTerrainConfig.from(settings, 1));
        assertNotNull(config.shaderConstants().build());
    }

    @Test
    void improvedTransparencyControlsMeshQuadSorting() {
        var settings = new ImpetusGameOptions.MeshTerrainSettings();
        var disabled = MeshTerrainConfig.from(settings, 12, false);
        assertEquals(ImpetusGameOptions.MeshTranslucencySorting.SECTIONS, disabled.sorting());
        assertTrue(disabled.shaderConstants().build().getDefineStrings().contains("#define SORT_SECTIONS"));
        assertFalse(disabled.shaderConstants().build().getDefineStrings().contains("#define SORT_QUADS"));

        var enabled = MeshTerrainConfig.from(settings, 12, true);
        assertEquals(ImpetusGameOptions.MeshTranslucencySorting.QUADS, enabled.sorting());
        assertTrue(enabled.shaderConstants().build().getDefineStrings().contains("#define SORT_QUADS"));
    }

    // Rejects a null name and an empty link.
    @Test
    void programBuilder() {
        assertThrows(NullPointerException.class, () -> MeshProgram.builder(null));
        MeshProgram.Builder builder = MeshProgram.builder("x");
        assertThrows(NullPointerException.class, () -> builder.constants(null));
        assertThrows(NullPointerException.class, () -> builder.stage(null, "p"));
        assertThrows(IllegalStateException.class, builder::link);
    }
}
