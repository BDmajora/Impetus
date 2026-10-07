package com.bdmajora.impetus.engine.impl.render.chunk;

import com.bdmajora.impetus.engine.impl.compat.environment.OsKind;
import com.bdmajora.impetus.engine.impl.gl.device.CommandList;
import com.bdmajora.impetus.engine.impl.gl.device.GLRenderDevice;
import com.bdmajora.impetus.engine.impl.gui.ImpetusGameOptions.MeshStatistics;
import com.bdmajora.impetus.engine.impl.gui.ImpetusGameOptions.MeshTranslucencySorting;
import com.bdmajora.impetus.engine.impl.render.ShaderModBridge;
import com.bdmajora.impetus.engine.impl.render.mesh.MeshShaderSupport;
import com.bdmajora.impetus.engine.impl.render.mesh.MeshTerrainConfig;
import com.bdmajora.impetus.engine.impl.render.mesh.MeshTerrainRenderer;
import com.bdmajora.impetus.engine.impl.render.viewport.CameraTransform;
import com.bdmajora.impetus.engine.impl.render.viewport.Viewport;
import com.bdmajora.impetus.engine.impl.util.PositionUtil;
import com.bdmajora.testing.Devices;
import com.bdmajora.testing.Passes;
import com.bdmajora.testing.Sections;
import com.bdmajora.testing.Statics;
import com.bdmajora.testing.TestFogService;
import com.bdmajora.testing.TestGl;
import com.bdmajora.testing.TestSectionManager;
import org.joml.Matrix4f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.Mockito;

import java.nio.IntBuffer;

import static org.junit.jupiter.api.Assertions.*;

@Timeout(value = 30, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class MeshTerrainSectionManagerTest {
    private static final ChunkRenderMatrices MATRICES = new ChunkRenderMatrices(new Matrix4f().perspective(1f, 1f, 0.1f, 1000f), new Matrix4f());
    private GLRenderDevice device;
    private CommandList commands;
    private TestSectionManager manager;

    @BeforeEach
    void activate() {
        device = Devices.active();
        commands = device.createCommandList();
        TestGl.meshCapable();
        Mockito.when(TestGl.gl().isExtensionSupported(Mockito.any())).thenReturn(true);
        Mockito.doAnswer(inv -> {
            inv.<IntBuffer>getArgument(2).put(0, 1);
            return 0x9119;
        }).when(TestGl.gl()).glGetSynci(Mockito.anyLong(), Mockito.anyInt(), Mockito.any());
        Statics.set(MeshShaderSupport.class, "supported", null);
        TestFogService.cutoff = 1_000_000f;
    }

    @AfterEach
    void deactivate() {
        if (manager != null) {
            manager.destroy();
        }
        TestFogService.cutoff = 1f;
        Statics.set(MeshShaderSupport.class, "supported", null);
        Statics.set(OsKind.class, "CURRENT", OsKind.LINUX);
        device.makeInactive();
    }

    private void frame(Viewport viewport, int number) {
        manager.trackViewport(viewport);
        manager.runAsyncTasks();
        manager.updateChunks(true);
        manager.uploadChunks();
        if (manager.needsUpdate()) {
            manager.update(viewport, number, false);
        }
        manager.tickVisibleRenders();
    }

    @Test
    void theMeshBackendOwnsGeometryAndDrawing() {
        var config = new MeshTerrainConfig(true, MeshTranslucencySorting.QUADS, MeshStatistics.QUADS, false, 512, 0, 4);
        manager = new TestSectionManager(commands, 4, 0, 2, config);
        MeshTerrainRenderer mesh = manager.getMeshTerrain();
        assertNotNull(mesh);
        assertTrue(ShaderModBridge.isNvidiumEnabled());

        manager.translucent.add(PositionUtil.packSection(0, 0, 0));
        manager.onChunkAdded(0, 0);
        Viewport viewport = Sections.viewport(8, 8, 8);
        frame(viewport, 1);
        frame(viewport, 2);
        // Both sections of the column built into the GPU store, none into the raster regions
        assertEquals(2, manager.builds.get());
        assertEquals(1, mesh.getPipeline().getSections().getRegions().getRegionCount());

        // Moving the camera queues no sort tasks, since the GPU keeps translucency ordered
        frame(Sections.viewport(9, 8, 8), 3);
        assertEquals(2, manager.builds.get());

        CameraTransform camera = viewport.getTransform();
        Mockito.clearInvocations(TestGl.gl());
        manager.renderLayer(MATRICES, Passes.MESH_SOLID, camera, camera);
        manager.renderLayer(MATRICES, Passes.MESH_CUTOUT, camera, camera);
        manager.renderLayer(MATRICES, Passes.MESH_TRANSLUCENT, camera, camera);
        // Region and section boxes, then the temporal and translucent multi-draws; the raster renderer never runs
        Mockito.verify(TestGl.gl(), Mockito.times(2)).glDrawMeshTasksNV(Mockito.eq(0), Mockito.anyInt());
        Mockito.verify(TestGl.gl(), Mockito.never()).glMultiDrawElementsBaseVertex(Mockito.anyInt(), Mockito.anyLong(), Mockito.anyInt(), Mockito.anyLong(), Mockito.anyInt(), Mockito.anyLong());

        assertTrue(manager.getDebugStrings().stream().anyMatch(line -> line.startsWith("Mesh terrain: ")));

        manager.onChunkRemoved(0, 0);
        assertEquals(0, mesh.getPipeline().getSections().getRegions().getRegionCount());
        manager.destroy();
        manager = null;
        assertFalse(MeshTerrainRenderer.isActive());
        assertFalse(ShaderModBridge.isNvidiumEnabled());
    }

    @Test
    void theOpaquePassWaitsForAViewport() {
        var config = new MeshTerrainConfig(false, MeshTranslucencySorting.NONE, MeshStatistics.NONE, false, 512, 0, 4);
        manager = new TestSectionManager(commands, 4, 0, 1, config);
        CameraTransform camera = Sections.viewport(8, 8, 8).getTransform();
        manager.renderLayer(MATRICES, Passes.MESH_SOLID, camera, camera);
        Mockito.verify(TestGl.gl(), Mockito.never()).glEnableClientState(Mockito.anyInt());
        // The base framebuffer size is a placeholder the platforms replace
        assertEquals(1, manager.getFramebufferWidth());
        assertEquals(1, manager.getFramebufferHeight());
    }
}
