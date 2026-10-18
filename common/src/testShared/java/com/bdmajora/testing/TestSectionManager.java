package com.bdmajora.testing;

import com.bdmajora.impetus.engine.impl.render.chunk.RenderPassConfiguration;
import com.bdmajora.impetus.engine.impl.render.mesh.MeshTerrainConfig;
import com.bdmajora.impetus.engine.impl.render.mesh.MeshTerrainShading;
import com.bdmajora.impetus.engine.impl.render.mesh.region.SectionGeometryPacker;

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
import com.bdmajora.impetus.engine.impl.render.chunk.compile.tasks.ChunkBuilderSortTask;
import com.bdmajora.impetus.engine.impl.render.chunk.compile.tasks.ChunkBuilderTask;
import com.bdmajora.impetus.engine.impl.render.chunk.data.BuiltRenderSectionData;
import com.bdmajora.impetus.engine.impl.render.chunk.data.BuiltSectionMeshParts;
import com.bdmajora.impetus.engine.impl.render.chunk.data.MinecraftBuiltRenderSectionData;
import com.bdmajora.impetus.engine.impl.render.chunk.fog.FogService;
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
    // The settable statics the chunk shaders also read
    private static final FogService FOG = new TestFogService();

    public AsyncOcclusionMode asyncMode = AsyncOcclusionMode.NONE;
    public boolean fogOcclusion;
    public boolean occlusionCulling;
    public boolean shadowPass;
    public boolean debugInfo;
    public boolean importantRebuilds;
    public boolean respectQueueLimit = true;
    // Sections that mesh to nothing at all
    public final LongOpenHashSet empty = new LongOpenHashSet();
    // Sections whose translucent geometry needs a partition tree, re-sorted when the camera crosses its cut plane
    public final LongOpenHashSet translucent = new LongOpenHashSet();
    // Sections whose translucent geometry can only be distance-sorted, every time the camera moves far enough
    public final LongOpenHashSet distanceSorted = new LongOpenHashSet();
    // Sections that carry a global block entity
    public final LongOpenHashSet globalEntities = new LongOpenHashSet();
    // Sections whose rebuild task is refused outright
    public final LongOpenHashSet refused = new LongOpenHashSet();
    public final AtomicInteger builds = new AtomicInteger();
    public final AtomicInteger sorts = new AtomicInteger();
    public final AtomicInteger invalidations = new AtomicInteger();

    // The pass set builds go into; the mesh constructor swaps in the mesh-layout passes
    private final RenderPassConfiguration<String> passes;
    // Set when this manager drives the mesh backend, so builds pack their geometry like the real meshing task
    private final boolean meshTerrain;

    public TestSectionManager(CommandList commandList, int renderDistance, int minSection, int maxSection, boolean hasShadowPass, AsyncOcclusionMode asyncMode, int threads) {
        super(Passes.CONFIG, () -> new ChunkBuildContext(Passes.CONFIG), (device, config) -> new Renderer(device), renderDistance, commandList, minSection, maxSection, threads, hasShadowPass);
        this.asyncMode = asyncMode;
        this.passes = Passes.CONFIG;
        this.meshTerrain = false;
    }

    // A manager whose terrain belongs to the mesh-shader backend; builds run inline
    public TestSectionManager(CommandList commandList, int renderDistance, int minSection, int maxSection, MeshTerrainConfig mesh) {
        super(Passes.MESH_CONFIG, () -> new ChunkBuildContext(Passes.MESH_CONFIG), (device, config) -> new Renderer(device), renderDistance, commandList, minSection, maxSection, -1, false, mesh);
        this.passes = Passes.MESH_CONFIG;
        this.meshTerrain = true;
    }

    // The same with outside mesh shading, as a shader pack brings, and a shadow pass to drive it
    public TestSectionManager(CommandList commandList, int renderDistance, int minSection, int maxSection, MeshTerrainConfig mesh, MeshTerrainShading shading) {
        super(Passes.MESH_CONFIG, () -> new ChunkBuildContext(Passes.MESH_CONFIG), (device, config) -> new Renderer(device), renderDistance, commandList, minSection, maxSection, -1, true, mesh, shading);
        this.passes = Passes.MESH_CONFIG;
        this.meshTerrain = true;
    }

    private static final class Renderer extends DefaultChunkRenderer {
        Renderer(RenderDevice device) {
            super(device, Passes.CONFIG, FOG);
        }

        @Override
        protected void configureShaderInterface(ChunkShaderInterface shader) {}
    }

    // The mode is read during construction, before the field can be set, so NONE is the constructor's answer
    @Override
    protected AsyncOcclusionMode getAsyncOcclusionMode() {
        return asyncMode == null ? AsyncOcclusionMode.NONE : asyncMode;
    }

    // Read during construction; the tests drive the CPU occlusion graph
    @Override
    protected boolean useRasterOcclusionCulling() {
        return false;
    }

    @Override
    public FogService getFogService() {
        return FOG;
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
        boolean distance = distanceSorted.contains(key);
        boolean sorted = distance || translucent.contains(key);
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
                ChunkModelBuilder solid = buffers.get(passes.defaultSolidMaterial().pass);
                solid.getVertexBuffer(ModelQuadFacing.POS_Y).push(quad(0, 1, 0, 0, 1, 0), passes.defaultSolidMaterial());
                if (sorted) {
                    ChunkModelBuilder glass = buffers.get(passes.defaultTranslucentMaterial().pass);
                    // The straddling pair is cut at local z=5
                    for (float[][] quad : distance ? Quads.pinwheel(4) : Quads.straddling(4)) {
                        glass.getVertexBuffer(ModelQuadFacing.UNASSIGNED).push(Quads.vertices(quad), passes.defaultTranslucentMaterial());
                    }
                }
                var meshes = BuiltSectionMeshParts.groupFromBuildBuffers(buffers, 0, 0, 0);
                var geometry = meshTerrain ? SectionGeometryPacker.pack(meshes, 0, 0, 0) : null;
                return new ChunkBuildOutput(render, data, meshes, geometry, frame);
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

    @Override
    public ChunkBuilderSortTask createSortTask(RenderSection render, int frame) {
        ChunkBuilderSortTask task = super.createSortTask(render, frame);
        if (task != null) {
            sorts.incrementAndGet();
        }
        return task;
    }

    // The loaded section at a position, or null
    public RenderSection section(int x, int y, int z) {
        long key = PositionUtil.packSection(x, y, z);
        for (RenderSection section : getAllRenderSections()) {
            if (section.positionAsLong() == key) {
                return section;
            }
        }
        return null;
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
