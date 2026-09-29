package com.bdmajora.impetus.engine.impl.render.chunk.compile;

import com.bdmajora.impetus.engine.api.util.ColorABGR;
import com.bdmajora.impetus.engine.impl.model.quad.properties.ModelQuadFacing;
import com.bdmajora.impetus.engine.impl.render.chunk.RenderPassConfiguration;
import com.bdmajora.impetus.engine.impl.render.chunk.compile.buffers.BakedChunkModelBuilder;
import com.bdmajora.impetus.engine.impl.render.chunk.compile.buffers.ChunkModelBuilder;
import com.bdmajora.impetus.engine.impl.render.chunk.data.BuiltRenderSectionData;
import com.bdmajora.impetus.engine.impl.render.chunk.data.BuiltSectionMeshParts;
import com.bdmajora.impetus.engine.impl.render.chunk.sorting.TranslucentQuadAnalyzer;
import com.bdmajora.impetus.engine.impl.render.chunk.terrain.TerrainRenderPass;
import com.bdmajora.impetus.engine.impl.render.chunk.vertex.builder.ChunkMeshBufferBuilder;
import com.bdmajora.impetus.engine.impl.render.chunk.vertex.format.ChunkVertexEncoder;
import com.bdmajora.testing.Passes;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class ChunkBuildBuffersTest {
    public static final RenderPassConfiguration<String> CONFIG = Passes.CONFIG;

    // A unit quad at height y, in canonical top-face order
    public static ChunkVertexEncoder.Vertex[] quad(float y) {
        ChunkVertexEncoder.Vertex[] quad = ChunkVertexEncoder.Vertex.uninitializedQuad();
        float[][] pos = {{0, y, 0}, {0, y, 1}, {1, y, 1}, {1, y, 0}};
        for (int i = 0; i < 4; i++) {
            quad[i].x = pos[i][0];
            quad[i].y = pos[i][1];
            quad[i].z = pos[i][2];
            quad[i].color = ColorABGR.pack(255, 255, 255, 255);
            quad[i].light = 0x00F000F0;
        }
        return quad;
    }

    @Test
    void buildsMeshesPerPassWithRangesAndSortedIndices() {
        ChunkBuildBuffers buffers = new ChunkBuildBuffers(CONFIG);
        assertSame(CONFIG, buffers.getRenderPassConfiguration());
        assertThrows(NullPointerException.class, () -> buffers.get(Passes.SOLID));
        BuiltRenderSectionData data = new BuiltRenderSectionData();
        buffers.init(data, 3);
        assertSame(data, buffers.getSectionContextBundle());
        assertNull(buffers.createMesh(Passes.SOLID, 0, 0, 0));
        ChunkModelBuilder solid = buffers.get(Passes.SOLID_MATERIAL);
        assertSame(solid, buffers.get(Passes.SOLID));
        assertSame(data, solid.getSectionContextBundle());
        assertNotNull(solid.getEncoder());
        assertNull(buffers.createMesh(Passes.SOLID, 0, 0, 0));
        solid.getVertexBuffer(ModelQuadFacing.POS_Y).push(quad(1), Passes.SOLID_MATERIAL);
        solid.getVertexBuffer(ModelQuadFacing.NEG_Y).push(quad(0), Passes.SOLID_MATERIAL);
        BuiltSectionMeshParts solidMesh = buffers.createMesh(Passes.SOLID, 0, 0, 0);
        assertNotNull(solidMesh);
        assertNull(solidMesh.indexBuffer());
        assertNull(solidMesh.sortState());
        assertEquals(2, solidMesh.ranges().size());
        assertEquals(4, solidMesh.ranges().get(ModelQuadFacing.NEG_Y).vertexStart());
        assertEquals(8 * 20, solidMesh.vertexBuffer().getLength());
        solidMesh.free();

        ChunkModelBuilder translucent = buffers.get(Passes.TRANSLUCENT);
        ChunkMeshBufferBuilder sortedBuffer = translucent.getVertexBuffer(ModelQuadFacing.POS_Y);
        assertSame(sortedBuffer, translucent.getVertexBuffer(ModelQuadFacing.UNASSIGNED));
        sortedBuffer.push(quad(0), Passes.TRANSLUCENT_MATERIAL);
        sortedBuffer.push(quad(4), Passes.TRANSLUCENT_MATERIAL);
        assertEquals(TranslucentQuadAnalyzer.Level.STATIC, sortedBuffer.getSortState().level());
        BuiltSectionMeshParts translucentMesh = buffers.createMesh(Passes.TRANSLUCENT, 0, 0, 0);
        assertNotNull(translucentMesh.indexBuffer());
        assertEquals(12 * 4, translucentMesh.indexBuffer().getLength());
        assertEquals(TranslucentQuadAnalyzer.Level.STATIC, translucentMesh.sortState().level());
        assertEquals(1, translucentMesh.ranges().size());
        var grouped = BuiltSectionMeshParts.groupFromBuildBuffers(buffers, 0, 0, 0);
        assertEquals(2, grouped.size());
        assertEquals(2, buffers.getBuilderPasses().size());
        buffers.init(data, 4);
        assertNull(buffers.createMesh(Passes.SOLID, 0, 0, 0));
        buffers.destroy();
        assertNull(buffers.getSectionContextBundle());
        translucentMesh.free();
        grouped.values().forEach(BuiltSectionMeshParts::free);

        ChunkBuildContext context = new ChunkBuildContext(CONFIG);
        assertSame(CONFIG, context.buffers.getRenderPassConfiguration());
        context.cleanup();
    }

    @Test
    void meshBufferBuilderGrowsAndSlices() {
        ChunkMeshBufferBuilder builder = new ChunkMeshBufferBuilder(Passes.SOLID.vertexType().createEncoder(), 20, 64, true);
        assertTrue(builder.isEmpty());
        assertThrows(IllegalStateException.class, builder::slice);
        builder.start(1);
        for (int i = 0; i < 10; i++) {
            builder.push(quad(i), Passes.SOLID_MATERIAL);
        }
        assertEquals(40, builder.count());
        assertEquals(800, builder.slice().remaining());
        assertNotNull(builder.getSortState());
        builder.resetSortState();
        assertSame(TranslucentQuadAnalyzer.SortState.NONE, builder.getSortState());
        builder.destroy();
        builder.destroy();
        ChunkMeshBufferBuilder plain = new ChunkMeshBufferBuilder(Passes.SOLID.vertexType().createEncoder(), 20, 64, false);
        plain.start(0);
        assertNull(plain.getSortState());
        plain.resetSortState();
        plain.destroy();

        BakedChunkModelBuilder baked = new BakedChunkModelBuilder(Passes.SOLID.vertexType().createEncoder(), 20, Passes.SOLID);
        assertThrows(NullPointerException.class, () -> baked.getVertexBuffer(ModelQuadFacing.POS_X));
        BuiltRenderSectionData data = new BuiltRenderSectionData();
        baked.begin(data, 0);
        assertTrue(baked.isEmpty());
        baked.getVertexBuffer(ModelQuadFacing.POS_X).push(quad(0), Passes.SOLID_MATERIAL);
        assertFalse(baked.isEmpty());
        assertSame(data, baked.getSectionContextBundle());
        baked.destroy();
    }

    @Test
    void globalContextBindsTheMainThread() {
        ChunkBuildContext context = new ChunkBuildContext(CONFIG);
        GlobalChunkBuildContext.setMainThread();
        GlobalChunkBuildContext.bindMainThread(context);
        assertSame(context, GlobalChunkBuildContext.get());
        Thread other = new Thread(() -> assertNull(GlobalChunkBuildContext.get()));
        other.start();
        join(other);
        ChunkBuildContext held = new ChunkBuildContext(CONFIG);
        class HolderThread extends Thread implements GlobalChunkBuildContext.Holder {
            ChunkBuildContext seen;

            HolderThread() {
                super(() -> {});
            }

            @Override
            public void run() {
                seen = GlobalChunkBuildContext.get();
            }

            @Override
            public ChunkBuildContext impetus$getGlobalContext() {
                return held;
            }
        }
        HolderThread holder = new HolderThread();
        holder.start();
        join(holder);
        assertSame(held, holder.seen);
    }

    private static void join(Thread thread) {
        try {
            thread.join();
        } catch (InterruptedException e) {
            throw new AssertionError(e);
        }
    }
}
