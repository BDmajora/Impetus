package com.bdmajora.impetus.engine.impl.render.mesh;

import com.bdmajora.impetus.engine.impl.ImpetusRuntimeOptions;
import com.bdmajora.impetus.engine.impl.compat.environment.OsKind;
import com.bdmajora.impetus.engine.impl.gui.ImpetusGameOptions.MeshStatistics;
import com.bdmajora.impetus.engine.impl.gui.ImpetusGameOptions.MeshTranslucencySorting;
import com.bdmajora.impetus.engine.impl.model.quad.properties.ModelQuadFacing;
import com.bdmajora.impetus.engine.impl.render.chunk.ChunkRenderMatrices;
import com.bdmajora.impetus.engine.impl.render.mesh.region.MeshStoresTest;
import com.bdmajora.impetus.engine.impl.render.mesh.region.SectionGeometry;
import com.bdmajora.impetus.engine.impl.render.viewport.Viewport;
import com.bdmajora.testing.Passes;
import com.bdmajora.testing.RecordingShading;
import com.bdmajora.testing.Sections;
import com.bdmajora.testing.Statics;
import com.bdmajora.testing.TestGl;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MeshRenderPipelineTest {
    private static final ChunkRenderMatrices MATRICES = new ChunkRenderMatrices(new Matrix4f().perspective(1f, 1f, 0.1f, 1000f), new Matrix4f());
    private static final MeshFog FOG = new MeshFog(1, 0, 10f, 100f, 0.1f, new float[]{0.5f, 0.6f, 0.7f, 1f});

    @BeforeEach
    void capable() {
        TestGl.meshCapable();
        Mockito.when(TestGl.gl().isExtensionSupported(Mockito.any())).thenReturn(true);
        Mockito.doAnswer(inv -> {
            inv.<IntBuffer>getArgument(2).put(0, 1);
            return 0x9119;
        }).when(TestGl.gl()).glGetSynci(Mockito.anyLong(), Mockito.anyInt(), Mockito.any());
        Statics.set(MeshShaderSupport.class, "supported", null);
    }

    @AfterEach
    void forget() {
        ImpetusRuntimeOptions.chunkFadeInDuration = 0;
        Statics.set(MeshShaderSupport.class, "supported", null);
        Statics.set(OsKind.class, "CURRENT", OsKind.LINUX);
    }

    static MeshTerrainConfig config(boolean temporal, MeshTranslucencySorting sorting, MeshStatistics statistics, int keep, int renderDistance) {
        return new MeshTerrainConfig(temporal, sorting, statistics, false, 64, keep, renderDistance);
    }

    private static MeshRenderPipeline pipeline(MeshTerrainConfig config) {
        return new MeshRenderPipeline(config, 64, 1 << 20, 1 << 16, 1 << 20);
    }

    private static SectionGeometry geometry() {
        return SectionGeometry.from(MeshStoresTest.mesh(2, ModelQuadFacing.POS_Y));
    }

    @Test
    void framesDrawLastFramesCommandsCullForTheNextAndSortTranslucency() {
        MeshRenderPipeline pipeline = pipeline(config(true, MeshTranslucencySorting.QUADS, MeshStatistics.QUADS, 0, 8));
        assertNotNull(pipeline.getSections());
        assertNotNull(pipeline.getUploadStream());
        assertEquals(64, pipeline.getMaxRegions());

        // Nothing stored: no draw at all, and the translucent pass has nothing either
        pipeline.renderFrame(Sections.viewport(8, 8, 8), MATRICES, FOG, 8, 8, 8, 800, 600);
        pipeline.renderTranslucent();
        pipeline.endFrame();
        Mockito.verify(TestGl.gl(), Mockito.never()).glDrawMeshTasksNV(Mockito.anyInt(), Mockito.anyInt());

        pipeline.getSections().upload(0, 0, 0, geometry());
        pipeline.getSections().upload(20, 0, 0, geometry());
        pipeline.renderFrame(new Viewport(Sections.NOTHING, new Vector3d(8, 8, 8)), MATRICES, FOG, 8, 8, 8, 800, 600);
        Mockito.verify(TestGl.gl(), Mockito.never()).glDrawMeshTasksNV(Mockito.anyInt(), Mockito.anyInt());

        // First visible frame: region and section boxes, the temporal draw of this frame's commands, and the section sort for the freshly uploaded regions
        pipeline.renderFrame(Sections.viewport(8.5, 8.5, 8.5), MATRICES, FOG, 8.5, 8.5, 8.5, 800, 600);
        Mockito.verify(TestGl.gl(), Mockito.times(2)).glDrawMeshTasksNV(0, 2);
        Mockito.verify(TestGl.gl(), Mockito.times(1)).glMultiDrawMeshTasksIndirectNV(0L, 2, 0);
        Mockito.verify(TestGl.gl()).glDispatchCompute(2, 1, 1);

        pipeline.renderTranslucent();
        Mockito.verify(TestGl.gl(), Mockito.times(2)).glMultiDrawMeshTasksIndirectNV(0L, 2, 0);
        pipeline.endFrame();

        // Second frame: the main draw replays last frame's commands too; the camera sits in both regions' x slab, so they sort again
        pipeline.renderFrame(Sections.viewport(8, 8, 8), MATRICES, FOG, 8, 8, 8, 800, 600);
        Mockito.verify(TestGl.gl(), Mockito.times(4)).glMultiDrawMeshTasksIndirectNV(0L, 2, 0);
        pipeline.renderTranslucent();
        pipeline.endFrame();

        List<String> lines = new ArrayList<>();
        pipeline.addDebugStrings(lines);
        assertEquals(List.of("Mesh statistics: F 2, R 0, S 0, Q 0"), lines);

        // Leaving the frustum wipes the stale section visibility
        pipeline.renderFrame(new Viewport(Sections.NOTHING, new Vector3d(8, 8, 8)), MATRICES, FOG, 8, 8, 8, 800, 600);
        Mockito.verify(TestGl.gl(), Mockito.atLeastOnce()).glClearNamedBufferSubDataZero(Mockito.anyInt(), Mockito.anyInt(), Mockito.anyLong(), Mockito.anyLong(), Mockito.anyInt(), Mockito.anyInt());

        // Nvidium's own programs have no shadow pass to draw
        Mockito.clearInvocations(TestGl.gl());
        pipeline.renderShadow(Sections.viewport(8, 8, 8), MATRICES, MeshCamera.at(8, 8, 8), null, 1024, 1024);
        pipeline.renderShadowTranslucent(null);
        Mockito.verify(TestGl.gl(), Mockito.never()).glMultiDrawMeshTasksIndirectNV(Mockito.anyLong(), Mockito.anyInt(), Mockito.anyInt());

        pipeline.evictRegion(0);
        assertNotNull(pipeline.getVisibilityTracker());
        pipeline.delete();
    }

    @Test
    void plainConfigurationsSkipTheOptionalPhases() {
        MeshRenderPipeline pipeline = pipeline(config(false, MeshTranslucencySorting.NONE, MeshStatistics.NONE, 0, 8));
        pipeline.getSections().upload(0, 0, 0, geometry());
        // A chunk fade-in duration goes into the scene as its inverse, for the task stages to fade new sections by
        ImpetusRuntimeOptions.chunkFadeInDuration = 250;
        pipeline.renderFrame(Sections.viewport(8, 8, 8), MATRICES, MeshFog.NONE, 8, 8, 8, 800, 600);
        pipeline.renderTranslucent();
        pipeline.endFrame();
        Mockito.verify(TestGl.gl(), Mockito.never()).glDispatchCompute(Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt());
        // No temporal pass: the first frame has no command to replay, so only the translucent pass multi-draws
        Mockito.verify(TestGl.gl(), Mockito.times(1)).glMultiDrawMeshTasksIndirectNV(0L, 1, 0);
        List<String> lines = new ArrayList<>();
        pipeline.addDebugStrings(lines);
        assertTrue(lines.isEmpty());

        // Statistics below REGIONS only report the CPU's frustum count
        MeshRenderPipeline frustumOnly = pipeline(config(false, MeshTranslucencySorting.SECTIONS, MeshStatistics.FRUSTUM, 0, 8));
        frustumOnly.addDebugStrings(lines);
        assertEquals(List.of("Mesh statistics: F 0"), lines);
        frustumOnly.delete();
        pipeline.delete();
    }

    @Test
    void regionsPastTheKeepDistanceAreEvicted() {
        MeshRenderPipeline pipeline = pipeline(config(false, MeshTranslucencySorting.NONE, MeshStatistics.REGIONS, 16, 8));
        pipeline.getSections().upload(0, 0, 0, geometry());
        pipeline.getSections().upload(400, 0, 0, geometry());
        pipeline.renderFrame(Sections.viewport(8, 8, 8), MATRICES, FOG, 8, 8, 8, 800, 600);
        assertEquals(1, pipeline.getSections().getRegions().getRegionCount());

        // Keeping everything never evicts for distance
        MeshRenderPipeline everything = pipeline(config(false, MeshTranslucencySorting.NONE, MeshStatistics.NONE, 256, 8));
        everything.getSections().upload(0, 0, 0, geometry());
        everything.getSections().upload(400, 0, 0, geometry());
        everything.renderFrame(Sections.viewport(8, 8, 8), MATRICES, FOG, 8, 8, 8, 800, 600);
        assertEquals(2, everything.getSections().getRegions().getRegionCount());
        everything.delete();
        pipeline.delete();
    }

    @Test
    void aProgramTheDriverRejectsFreesTheOnesAlreadyBuilt() {
        Mockito.when(TestGl.gl().glGetProgrami(Mockito.anyInt(), Mockito.eq(0x8B82))).thenReturn(1, 1, 0);
        assertThrows(RuntimeException.class, () -> pipeline(config(true, MeshTranslucencySorting.QUADS, MeshStatistics.NONE, 0, 8)));
        // Two linked programs freed, plus the one that failed to link
        Mockito.verify(TestGl.gl(), Mockito.times(3)).glDeleteProgram(Mockito.anyInt());
        Mockito.verify(TestGl.gl(), Mockito.never()).glCreateBuffers();

        Mockito.when(TestGl.gl().glGetProgrami(Mockito.anyInt(), Mockito.eq(0x8B82))).thenReturn(1, 1, 1, 0);
        assertThrows(RuntimeException.class, () -> pipeline(config(true, MeshTranslucencySorting.QUADS, MeshStatistics.NONE, 0, 8)));
        Mockito.when(TestGl.gl().glGetProgrami(Mockito.anyInt(), Mockito.eq(0x8B82))).thenReturn(1, 1, 1, 0);
        assertThrows(RuntimeException.class, () -> pipeline(config(false, MeshTranslucencySorting.QUADS, MeshStatistics.NONE, 0, 8)));
    }

    @Test
    void anOutsideShadingDrawsEveryPhaseIncludingTheShadowMap() {
        RecordingShading shading = new RecordingShading();
        MeshRenderPipeline pipeline = new MeshRenderPipeline(config(true, MeshTranslucencySorting.QUADS, MeshStatistics.NONE, 0, 8),
                64, 1 << 20, 1 << 16, 1 << 20, shading);
        pipeline.getSections().upload(0, 0, 0, geometry());
        MeshCamera camera = MeshCamera.of(MATRICES, 8, 8, 8);

        // The shadow map comes first in a frame and draws every region its frustum reaches from CPU-written commands
        pipeline.renderShadow(Sections.viewport(8, 8, 8), MATRICES, camera, Passes.SOLID, 1024, 1024);
        pipeline.renderShadowTranslucent(Passes.TRANSLUCENT);
        Mockito.verify(TestGl.gl(), Mockito.times(2)).glMultiDrawMeshTasksIndirectNV(0L, 1, 0);
        assertEquals(List.of("opaque SHADOW", "finish", "translucent SHADOW", "finish"), shading.calls);

        shading.calls.clear();
        pipeline.renderFrame(Sections.viewport(8, 8, 8), MATRICES, FOG, camera, 800, 600, Passes.SOLID);
        pipeline.renderTranslucent(Passes.TRANSLUCENT);
        pipeline.endFrame();
        assertEquals(List.of("opaque MAIN", "opaque TEMPORAL", "finish", "translucent MAIN", "finish"), shading.calls);
        // Every phase leaves no program bound for the fixed-function draws that follow terrain
        Mockito.verify(TestGl.gl(), Mockito.atLeast(4)).glUseProgram(0);

        // Shading with nothing to draw with issues no draw at all, and no shadow programs means no shadow pass
        Mockito.clearInvocations(TestGl.gl());
        shading.bound = false;
        pipeline.renderShadow(Sections.viewport(8, 8, 8), MATRICES, camera, Passes.SOLID, 1024, 1024);
        pipeline.renderShadowTranslucent(Passes.TRANSLUCENT);
        pipeline.renderFrame(Sections.viewport(8, 8, 8), MATRICES, FOG, camera, 800, 600, Passes.SOLID);
        pipeline.renderTranslucent(Passes.TRANSLUCENT);
        pipeline.endFrame();
        Mockito.verify(TestGl.gl(), Mockito.never()).glMultiDrawMeshTasksIndirectNV(Mockito.anyLong(), Mockito.anyInt(), Mockito.anyInt());
        shading.shadows = false;
        shading.calls.clear();
        pipeline.renderShadow(Sections.viewport(8, 8, 8), MATRICES, camera, Passes.SOLID, 1024, 1024);
        pipeline.renderShadowTranslucent(Passes.TRANSLUCENT);
        assertTrue(shading.calls.isEmpty());

        pipeline.delete();
        assertEquals(List.of("delete"), shading.calls);
    }

    @Test
    void theShadowMapSkipsWhatItsFrustumMisses() {
        RecordingShading shading = new RecordingShading();
        MeshRenderPipeline pipeline = new MeshRenderPipeline(config(false, MeshTranslucencySorting.NONE, MeshStatistics.NONE, 0, 8),
                64, 1 << 20, 1 << 16, 1 << 20, shading);
        MeshCamera camera = MeshCamera.at(8, 8, 8);
        // Nothing stored, then nothing inside the frustum
        pipeline.renderShadow(Sections.viewport(8, 8, 8), MATRICES, camera, Passes.SOLID, 512, 512);
        pipeline.getSections().upload(0, 0, 0, geometry());
        pipeline.renderShadow(new Viewport(Sections.NOTHING, new Vector3d(8, 8, 8)), MATRICES, camera, Passes.SOLID, 512, 512);
        pipeline.renderShadowTranslucent(Passes.TRANSLUCENT);
        assertTrue(shading.calls.isEmpty());
        pipeline.delete();
    }

    @Test
    void anOutsideShadingIsFreedWhenThePipelineCannotStart() {
        RecordingShading shading = new RecordingShading();
        Mockito.when(TestGl.gl().glGetProgrami(Mockito.anyInt(), Mockito.eq(0x8B82))).thenReturn(0);
        assertThrows(RuntimeException.class, () -> new MeshRenderPipeline(config(false, MeshTranslucencySorting.NONE, MeshStatistics.NONE, 0, 8),
                64, 1 << 20, 1 << 16, 1 << 20, shading));
        assertEquals(List.of("delete"), shading.calls);
    }

    @Test
    void sceneBlockMatchesTheShaderLayout() {
        // 128 bytes of matrix and vectors, eleven pointers, then 39 bytes of scalars, rounded to 16
        assertEquals(256, MeshRenderPipeline.SCENE_BYTES);
        assertEquals(16, MeshRenderPipeline.align(16, 16));
        assertEquals(32, MeshRenderPipeline.align(17, 16));
    }
}
