package com.bdmajora.testing;

import com.bdmajora.impetus.engine.api.util.ColorABGR;
import com.bdmajora.impetus.engine.impl.gl.device.CommandList;
import com.bdmajora.impetus.engine.impl.gl.device.RenderDevice;
import com.bdmajora.impetus.engine.impl.model.quad.properties.ModelQuadFacing;
import com.bdmajora.impetus.engine.impl.render.chunk.DefaultChunkRenderer;
import com.bdmajora.impetus.engine.impl.render.chunk.RenderSection;
import com.bdmajora.impetus.engine.impl.render.chunk.RenderSectionManager;
import com.bdmajora.impetus.engine.impl.render.chunk.compile.ChunkBuildContext;
import com.bdmajora.impetus.engine.impl.render.chunk.compile.ChunkBuildOutput;
import com.bdmajora.impetus.engine.impl.render.chunk.compile.buffers.ChunkModelBuilder;
import com.bdmajora.impetus.engine.impl.render.chunk.compile.tasks.ChunkBuilderTask;
import com.bdmajora.impetus.engine.impl.render.chunk.data.BuiltRenderSectionData;
import com.bdmajora.impetus.engine.impl.render.chunk.data.BuiltSectionMeshParts;
import com.bdmajora.impetus.engine.impl.render.chunk.data.MinecraftBuiltRenderSectionData;
import com.bdmajora.impetus.engine.impl.render.chunk.lists.SectionTicker;
import com.bdmajora.impetus.engine.impl.render.chunk.occlusion.AsyncOcclusionMode;
import com.bdmajora.impetus.engine.impl.render.chunk.occlusion.VisibilityEncoding;
import com.bdmajora.impetus.engine.impl.render.chunk.shader.ChunkShaderInterface;
import com.bdmajora.impetus.engine.impl.render.chunk.sprite.GenericSectionSpriteTicker;
import com.bdmajora.impetus.engine.impl.render.chunk.vertex.format.ChunkVertexEncoder;
import com.bdmajora.impetus.engine.impl.render.viewport.Viewport;
import com.bdmajora.impetus.engine.impl.util.PositionUtil;
import com.bdmajora.impetus.engine.impl.util.task.CancellationToken;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

// A section manager whose builds run on the calling thread and mesh whatever the test says a section contains
public class TestSectionManager extends RenderSectionManager {
    public AsyncOcclusionMode asyncMode = AsyncOcclusionMode.NONE;
    public boolean fogOcclusion;
    public boolean occlusionCulling;
    public boolean shadowPass;
    public boolean debugInfo;
    public boolean importantRebuilds;
    public boolean respectQueueLimit = true;
    // Sections that mesh to nothing at all
    public final LongOpenHashSet empty = new LongOpenHashSet();
    // Sections that build translucent geometry with several normals, so they need dynamic sorting
    public final LongOpenHashSet translucent = new LongOpenHashSet();
    // Sections whose translucent geometry has too many normals for the trigger index
    public final LongOpenHashSet manyNormals = new LongOpenHashSet();
    // Sections that carry a global block entity
    public final LongOpenHashSet globalEntities = new LongOpenHashSet();
    // Sections whose rebuild task is refused outright
    public final LongOpenHashSet refused = new LongOpenHashSet();
    public final AtomicInteger builds = new AtomicInteger();
    public final AtomicInteger invalidations = new AtomicInteger();

    public TestSectionManager(CommandList commandList, int renderDistance, int minSection, int maxSection, boolean hasShadowPass, AsyncOcclusionMode asyncMode, int threads) {
        super(Passes.CONFIG, () -> new ChunkBuildContext(Passes.CONFIG), (device, config) -> new Renderer(device), renderDistance, commandList, minSection, maxSection, threads, hasShadowPass);
        this.asyncMode = asyncMode;
    }

    private static final class Renderer extends DefaultChunkRenderer {
        Renderer(RenderDevice device) {
            super(device, Passes.CONFIG);
        }

        @Override
        protected void configureShaderInterface(ChunkShaderInterface shader) {}
    }

    // The mode is read during construction, before the field can be set, so NONE is the constructor's answer
    @Override
    protected AsyncOcclusionMode getAsyncOcclusionMode() {
        return asyncMode == null ? AsyncOcclusionMode.NONE : asyncMode;
    }

    @Override
    protected SectionTicker createSectionTicker() {
        return new GenericSectionSpriteTicker<>(sprite -> {});
    }

    @Override
    public boolean isInShadowPass() {
        return shadowPass;
    }

    @Override
    protected boolean isDebugInfoShown() {
        return debugInfo;
    }

    @Override
    protected boolean allowImportantRebuilds() {
        return importantRebuilds;
    }

    @Override
    protected boolean shouldRespectUpdateTaskQueueSizeLimit() {
        return respectQueueLimit;
    }

    @Override
    protected boolean useFogOcclusion() {
        return fogOcclusion;
    }

    @Override
    protected boolean shouldUseOcclusionCulling(Viewport viewport, boolean spectator) {
        return occlusionCulling && !spectator;
    }

    @Override
    protected boolean isSectionVisuallyEmpty(int x, int y, int z) {
        return empty.contains(PositionUtil.packSection(x, y, z));
    }

    @Override
    protected void invalidateCachedSectionData(RenderSection section) {
        invalidations.incrementAndGet();
    }

    @Override
    protected ChunkBuilderTask<ChunkBuildOutput> createRebuildTask(RenderSection render, int frame) {
        long key = render.positionAsLong();
        if (refused.contains(key)) {
            return null;
        }
        boolean sorted = translucent.contains(key);
        boolean overflow = manyNormals.contains(key);
        boolean global = globalEntities.contains(key);
        return new ChunkBuilderTask<>() {
            @Override
            public ChunkBuildOutput execute(ChunkBuildContext context, CancellationToken cancellationToken) {
                builds.incrementAndGet();
                MinecraftBuiltRenderSectionData<String, Integer> data = new MinecraftBuiltRenderSectionData<>();
                data.hasBlockGeometry = true;
                data.visibilityData = VisibilityEncoding.EVERYTHING;
                data.animatedSprites.add("sprite");
                if (global) {
                    data.globalBlockEntities.add(1);
                }
                var buffers = context.buffers;
                buffers.init(data, render.getSectionIndex());
                ChunkModelBuilder solid = buffers.get(Passes.SOLID);
                solid.getVertexBuffer(ModelQuadFacing.POS_Y).push(quad(0, 1, 0, 0, 1, 0), Passes.SOLID_MATERIAL);
                if (sorted) {
                    ChunkModelBuilder glass = buffers.get(Passes.TRANSLUCENT);
                    // Two quads a quarter turn apart are distinct normals; twenty at 4.5 degree steps overflow the trigger index's normal table
                    int normals = overflow ? 20 : 2;
                    for (int i = 0; i < normals; i++) {
                        float angle = (float) (i * (overflow ? Math.PI / 40 : Math.PI / 2));
                        glass.getVertexBuffer(ModelQuadFacing.UNASSIGNED).push(quad(0, 0, 0, (float) Math.cos(angle), 1, (float) Math.sin(angle)), Passes.TRANSLUCENT_MATERIAL);
                    }
                }
                var meshes = BuiltSectionMeshParts.groupFromBuildBuffers(buffers, 0, 0, 0);
                return new ChunkBuildOutput(render, data, meshes, frame);
            }
        };
    }

    // A quad spanning from (x0,y0,z0) up to (x1,y1,z1) with a vertical edge, so different (x1,z1) give different normals
    private static ChunkVertexEncoder.Vertex[] quad(float x0, float y0, float z0, float x1, float y1, float z1) {
        ChunkVertexEncoder.Vertex[] quad = ChunkVertexEncoder.Vertex.uninitializedQuad();
        float[][] pos = {{x0, y0, z0}, {x0, y1, z0}, {x1, y1, z1}, {x1, y0, z1}};
        for (int i = 0; i < 4; i++) {
            quad[i].x = pos[i][0];
            quad[i].y = pos[i][1];
            quad[i].z = pos[i][2];
            quad[i].color = ColorABGR.pack(255, 255, 255, 255);
            quad[i].light = 0x00F000F0;
        }
        return quad;
    }

    // Every section key currently loaded, for assertions
    public List<Long> keys() {
        List<Long> keys = new ArrayList<>();
        for (RenderSection section : getAllRenderSections()) {
            keys.add(section.positionAsLong());
        }
        return keys;
    }
}
