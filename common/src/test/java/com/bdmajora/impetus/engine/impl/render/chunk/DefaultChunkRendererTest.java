package com.bdmajora.impetus.engine.impl.render.chunk;

import com.bdmajora.impetus.engine.impl.common.util.NativeBuffer;
import com.bdmajora.impetus.engine.impl.gl.arena.PendingUpload;
import com.bdmajora.impetus.engine.impl.gl.device.CommandList;
import com.bdmajora.impetus.engine.impl.gl.device.GLRenderDevice;
import com.bdmajora.impetus.engine.impl.gl.util.VertexRange;
import com.bdmajora.impetus.engine.impl.model.quad.properties.ModelQuadFacing;
import com.bdmajora.impetus.engine.impl.render.chunk.compile.ChunkBuildBuffersTest;
import com.bdmajora.impetus.engine.impl.render.chunk.lists.VisibleChunkCollector;
import com.bdmajora.impetus.engine.impl.render.chunk.lists.SortedRenderLists;
import com.bdmajora.impetus.engine.impl.render.chunk.occlusion.OcclusionNode;
import com.bdmajora.impetus.engine.impl.render.chunk.region.RenderRegionManager;
import com.bdmajora.impetus.engine.impl.render.chunk.shader.ChunkShaderInterface;
import com.bdmajora.impetus.engine.impl.render.chunk.terrain.TerrainRenderPass;
import com.bdmajora.impetus.engine.impl.render.viewport.CameraTransform;
import com.bdmajora.testing.Devices;
import com.bdmajora.testing.Passes;
import com.bdmajora.testing.Sections;
import com.bdmajora.testing.TestGl;
import org.joml.Matrix4f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class DefaultChunkRendererTest {
    private GLRenderDevice device;
    private CommandList commands;
    private RenderRegionManager regions;

    @BeforeEach
    void activate() {
        device = Devices.active();
        commands = device.createCommandList();
        regions = new RenderRegionManager(commands);
        Mockito.when(TestGl.gl().glGetUniformLocation(Mockito.anyInt(), Mockito.any())).thenReturn(1);
    }

    @AfterEach
    void deactivate() {
        regions.delete(commands);
        device.makeInactive();
    }

    // A section with real geometry in the given pass, so the region has storage and a slice mask
    private RenderSection built(int x, int z, TerrainRenderPass pass, boolean indexed) {
        RenderSection section = Sections.section(regions, x, 0, z);
        var resources = section.getRegion().createResources(pass.vertexType().getVertexFormat(), commands);
        PendingUpload vertices = PendingUpload.of(new NativeBuffer(4 * pass.vertexType().getVertexFormat().getStride()));
        resources.getGeometryArena().upload(commands, new ArrayList<>(List.of(vertices)));
        PendingUpload indices = null;
        if (indexed) {
            indices = PendingUpload.of(new NativeBuffer(24));
            resources.getOrCreateIndexArena(commands).upload(commands, new ArrayList<>(List.of(indices)));
        }
        section.getRegion().createStorage(pass).setMeshes(section.getSectionIndex(), vertices.getResult(), indices == null ? null : indices.getResult(),
                Map.of(ModelQuadFacing.POS_Y, new VertexRange(0, 4)));
        return section;
    }

    private SortedRenderLists lists(RenderSection... sections) {
        VisibleChunkCollector collector = new VisibleChunkCollector(1, regions.getRegionIdsLength(), 8);
        for (RenderSection section : sections) {
            collector.visit(new OcclusionNode(section), true);
        }
        return collector.createRenderLists();
    }

    private static final class Renderer extends DefaultChunkRenderer {
        final AtomicInteger configured = new AtomicInteger();
        boolean cull = true;

        Renderer(GLRenderDevice device) {
            super(device, ChunkBuildBuffersTest.CONFIG);
        }

        // The base default is culling on; the flag only turns it off
        @Override
        protected boolean useBlockFaceCulling() {
            return cull && super.useBlockFaceCulling();
        }

        @Override
        protected void configureShaderInterface(ChunkShaderInterface shader) {
            configured.incrementAndGet();
        }
    }

    @Test
    void rendersVisibleRegionsThroughASharedIndexBuffer() {
        Renderer renderer = new Renderer(device);
        assertSame(ChunkBuildBuffersTest.CONFIG, renderer.getRenderPassConfiguration());
        RenderSection near = built(0, 0, Passes.SOLID, false);
        RenderSection far = built(20, 20, Passes.SOLID, false);
        Sections.section(regions, 1, 0, 0);
        ChunkRenderMatrices matrices = new ChunkRenderMatrices(new Matrix4f(), new Matrix4f());
        CameraTransform camera = new CameraTransform(8.5, 20, 8.5);
        renderer.render(matrices, commands, lists(near, far), Passes.SOLID, camera, camera);
        assertEquals(1, renderer.configured.get());
        Mockito.verify(TestGl.gl(), Mockito.times(2)).glMultiDrawElementsBaseVertex(Mockito.anyInt(), Mockito.anyLong(), Mockito.anyInt(), Mockito.anyLong(), Mockito.anyInt(), Mockito.anyLong());
        // The second frame reuses the program and the region tessellations
        renderer.render(matrices, commands, lists(near, far), Passes.SOLID, camera, camera);
        Mockito.verify(TestGl.gl(), Mockito.times(1)).glLinkProgram(Mockito.anyInt());
        // Looking from below, the top faces are culled and nothing draws
        CameraTransform below = new CameraTransform(8.5, -100, 8.5);
        renderer.render(matrices, commands, lists(near), Passes.SOLID, below, below);
        Mockito.verify(TestGl.gl(), Mockito.times(4)).glMultiDrawElementsBaseVertex(Mockito.anyInt(), Mockito.anyLong(), Mockito.anyInt(), Mockito.anyLong(), Mockito.anyInt(), Mockito.anyLong());
        renderer.cull = false;
        renderer.render(matrices, commands, lists(near), Passes.SOLID, below, below);
        Mockito.verify(TestGl.gl(), Mockito.times(5)).glMultiDrawElementsBaseVertex(Mockito.anyInt(), Mockito.anyLong(), Mockito.anyInt(), Mockito.anyLong(), Mockito.anyInt(), Mockito.anyLong());
        renderer.render(matrices, commands, lists(near), Passes.CUTOUT, camera, camera);
        renderer.render(matrices, commands, SortedRenderLists.empty(), Passes.SOLID, camera, camera);
        renderer.delete(commands);
    }

    @Test
    void sortedPassesUseTheRegionIndexBufferInReverse() {
        Renderer renderer = new Renderer(device);
        RenderSection a = built(0, 0, Passes.TRANSLUCENT, true);
        RenderSection b = built(30, 0, Passes.TRANSLUCENT, true);
        ChunkRenderMatrices matrices = new ChunkRenderMatrices(new Matrix4f(), new Matrix4f());
        CameraTransform camera = new CameraTransform(0, 0, 0);
        renderer.render(matrices, commands, lists(a, b), Passes.TRANSLUCENT, camera, camera);
        Mockito.verify(TestGl.gl(), Mockito.times(2)).glMultiDrawElementsBaseVertex(Mockito.anyInt(), Mockito.anyLong(), Mockito.anyInt(), Mockito.anyLong(), Mockito.anyInt(), Mockito.anyLong());
        renderer.render(matrices, commands, lists(a, b), Passes.TRANSLUCENT, camera, camera);
        renderer.delete(commands);
    }

    @Test
    void legacyContextsPatchTheShadersAndCompileFailuresDisableRendering() {
        Mockito.when(TestGl.gl().isOpenGLVersionSupported(3, 2)).thenReturn(false);
        Renderer legacy = new Renderer(device);
        RenderSection near = built(0, 0, Passes.SOLID, false);
        ChunkRenderMatrices matrices = new ChunkRenderMatrices(new Matrix4f(), new Matrix4f());
        CameraTransform camera = new CameraTransform(8.5, 20, 8.5);
        legacy.render(matrices, commands, lists(near), Passes.SOLID, camera, camera);
        Mockito.verify(TestGl.gl(), Mockito.never()).glBindFragDataLocation(Mockito.anyInt(), Mockito.anyInt(), Mockito.any());
        Mockito.verify(TestGl.gl(), Mockito.atLeastOnce()).glShaderSourceSafe(Mockito.anyInt(), Mockito.argThat(src -> src.toString().startsWith("#version 120")));
        legacy.delete(commands);

        Mockito.when(TestGl.gl().glGetShaderi(Mockito.anyInt(), Mockito.eq(0x8B81))).thenReturn(0);
        Renderer broken = new Renderer(device);
        broken.render(matrices, commands, lists(near), Passes.CUTOUT, camera, camera);
        broken.render(matrices, commands, lists(near), Passes.CUTOUT, camera, camera);
        assertEquals(0, broken.configured.get());
        broken.delete(commands);
    }
}
