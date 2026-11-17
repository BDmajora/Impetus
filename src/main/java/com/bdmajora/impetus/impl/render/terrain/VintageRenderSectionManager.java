package com.bdmajora.impetus.impl.render.terrain;

import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.Chunk;
import com.bdmajora.fulgor.FulgorRenderBridge;
import com.bdmajora.impetus.engine.impl.gl.device.CommandList;
import com.bdmajora.impetus.engine.impl.gl.device.RenderDevice;
import com.bdmajora.impetus.engine.impl.render.chunk.*;
import com.bdmajora.impetus.engine.impl.render.chunk.compile.ChunkBuildOutput;
import com.bdmajora.impetus.engine.impl.render.chunk.compile.tasks.ChunkBuilderTask;
import com.bdmajora.impetus.engine.impl.render.chunk.data.BuiltRenderSectionData;
import com.bdmajora.impetus.engine.impl.render.chunk.data.MinecraftBuiltRenderSectionData;
import com.bdmajora.impetus.engine.impl.render.chunk.lists.SectionTicker;
import com.bdmajora.impetus.engine.impl.render.chunk.occlusion.AsyncOcclusionMode;
import com.bdmajora.impetus.engine.impl.render.chunk.shader.ChunkShaderInterface;
import com.bdmajora.impetus.engine.impl.render.chunk.shader.ChunkShaderTextureSlot;
import com.bdmajora.impetus.engine.impl.render.chunk.sprite.GenericSectionSpriteTicker;
import com.bdmajora.impetus.engine.impl.render.chunk.vertex.format.ChunkVertexType;
import com.bdmajora.impetus.engine.impl.render.mesh.MeshTerrainConfig;
import com.bdmajora.impetus.engine.impl.render.mesh.MeshTerrainShading;
import com.bdmajora.impetus.engine.impl.render.viewport.Viewport;
import com.bdmajora.impetus.engine.impl.util.position.SectionPos;
import org.jetbrains.annotations.Nullable;
import com.bdmajora.impetus.ImpetusVintage;
import com.bdmajora.impetus.impl.render.terrain.compile.VintageChunkBuildContext;
import com.bdmajora.impetus.impl.render.terrain.compile.task.ChunkBuilderMeshingTask;
import com.bdmajora.impetus.impl.render.terrain.sprite.SpriteUtil;
import com.bdmajora.impetus.impl.world.WorldSlice;
import com.bdmajora.impetus.impl.world.cloned.ChunkRenderContext;
import com.bdmajora.impetus.impl.world.cloned.ClonedChunkSectionCache;

import com.bdmajora.impetus.engine.impl.render.chunk.fog.FogService;
import com.bdmajora.impetus.impl.render.terrain.fog.GLStateManagerFogService;
import java.util.List;

// Section manager of the vintage world.
public class VintageRenderSectionManager extends RenderSectionManager {
    // World of the manager.
    private final WorldClient world;
    // Cache of cloned sections.
    @Getter
    private final ClonedChunkSectionCache sectionCache;

    // Creates a vintage render section manager from its configuration and world and render distance and command list and min section and max section.
    public VintageRenderSectionManager(RenderPassConfiguration<?> configuration, WorldClient world, int renderDistance, CommandList commandList, int minSection, int maxSection,
                                       @Nullable MeshTerrainConfig meshTerrain, @Nullable MeshTerrainShading meshShading) {
        super(configuration, () -> new VintageChunkBuildContext(world, configuration), ChunkRenderer::new, renderDistance, commandList, minSection, maxSection, ImpetusVintage.options().performance.chunkBuilderThreads, true, meshTerrain, meshShading);
        this.world = world;
        this.sectionCache = new ClonedChunkSectionCache(world);
    }

    // Returns whether the shadow pass is running.
    @Override
    public boolean isInShadowPass() {
        return com.bdmajora.impetus.umbra.pipeline.UmbraShadowRenderer.isShadowPass();
    }

    // Creates a manager with the default command list.
    public static VintageRenderSectionManager create(ChunkVertexType vertexType, WorldClient world, int renderDistance, CommandList commandList,
                                                     @Nullable MeshTerrainConfig meshTerrain) {
        return create(vertexType, world, renderDistance, commandList, meshTerrain, null);
    }

    // Creates a manager with a command list.
    public static VintageRenderSectionManager create(ChunkVertexType vertexType, WorldClient world, int renderDistance, CommandList commandList,
                                                     @Nullable MeshTerrainConfig meshTerrain, @Nullable MeshTerrainShading meshShading) {
        return new VintageRenderSectionManager(VintageRenderPassConfigurationBuilder.build(vertexType), world, renderDistance, commandList, 0, 16,
                meshTerrain, meshShading);
    }

    // Returns the framebuffer width.
    @Override
    protected int getFramebufferWidth() {
        return Minecraft.getMinecraft().displayWidth;
    }

    // Returns the framebuffer height.
    @Override
    protected int getFramebufferHeight() {
        return Minecraft.getMinecraft().displayHeight;
    }

    // Returns whether raster occlusion culling is on.
    @Override
    protected boolean useRasterOcclusionCulling() {
        return ImpetusVintage.options().performance.useRasterOcclusionCulling;
    }

    // Returns the fog service.
    @Override
    public FogService getFogService() {
        return GLStateManagerFogService.INSTANCE;
    }

    // Returns the async occlusion mode.
    @Override
    protected AsyncOcclusionMode getAsyncOcclusionMode() {
        return ImpetusVintage.options().performance.asyncOcclusionMode;
    }

    // Returns true.
    @Override
    protected boolean shouldRespectUpdateTaskQueueSizeLimit() {
        return true;
    }

    // Returns whether fog occlusion is on.
    @Override
    protected boolean useFogOcclusion() {
        return ImpetusVintage.options().performance.useFogOcclusion;
    }

    // Returns whether use occlusion culling.
    @Override
    protected boolean shouldUseOcclusionCulling(Viewport positionedViewport, boolean spectator) {
        com.bdmajora.impetus.umbra.pipeline.UmbraRenderingPipeline pipeline =
                com.bdmajora.impetus.umbra.Umbra.getRenderingPipeline();
        if (pipeline != null && pipeline.shouldDisableOcclusionCulling()) {
            return false;
        }
        if (spectator) {
            var camBlockPos = positionedViewport.getBlockCoord();

            if (this.world.getBlockState(new BlockPos(camBlockPos.x(), camBlockPos.y(), camBlockPos.z())).isOpaqueCube()) {
                return false;
            }
        }

        return Minecraft.getMinecraft().renderChunksMany;
    }

    // Returns whether a section has nothing to draw.
    @Override
    protected boolean isSectionVisuallyEmpty(int x, int y, int z) {
        Chunk chunk = this.world.getChunk(x, z);
        if (chunk.isEmpty()) {
            return true;
        }
        var array = chunk.getBlockStorageArray();
        if (y < 0 || y >= array.length) {
            return true;
        }
        return array[y] == Chunk.NULL_BLOCK_STORAGE || FulgorRenderBridge.isEmptyOfBlocks(array[y]);
    }

    // Returns a rebuild task for a section or null.
    @Override
    protected @Nullable ChunkBuilderTask<ChunkBuildOutput> createRebuildTask(RenderSection render, int frame) {
        ChunkRenderContext context = WorldSlice.prepare(this.world, new SectionPos(render.getChunkX(), render.getChunkY(), render.getChunkZ()), this.sectionCache);

        if (context == null) {
            return null;
        }

        return new ChunkBuilderMeshingTask(render, context, frame, this.cameraPosition);
    }

    // Returns whether rebuilds may run at once.
    @Override
    protected boolean allowImportantRebuilds() {
        return !ImpetusVintage.options().performance.alwaysDeferChunkUpdates;
    }

    // Schedules the section for rebuild.
    @Override
    protected void scheduleSectionForRebuild(int x, int y, int z, boolean important) {
        this.sectionCache.invalidate(x, y, z);
        super.scheduleSectionForRebuild(x, y, z, important);
    }

    // Updates the chunks.
    @Override
    public void updateChunks(boolean updateImmediately) {
        this.sectionCache.cleanup();
        super.updateChunks(updateImmediately);
    }

    // Adds the render boxes of block entities to a list.
    @SuppressWarnings("unchecked")
    private static void retrieveBBForList(List<?> blockEntities) {
        if (!blockEntities.isEmpty()) {
            for (var be : (List<TileEntity>)blockEntities) {
                be.getRenderBoundingBox();
            }
        }
    }

    // Updates the info of a section and returns whether it changed.
    @Override
    protected boolean updateSectionInfo(RenderSection render, @Nullable BuiltRenderSectionData info) {
        if (info instanceof MinecraftBuiltRenderSectionData<?,?> mcData) {
            retrieveBBForList(mcData.culledBlockEntities);
            retrieveBBForList(mcData.globalBlockEntities);
        }
        return super.updateSectionInfo(render, info);
    }

    // Returns the ticker of a section or null.
    @Override
    protected @Nullable SectionTicker createSectionTicker() {
        return new GenericSectionSpriteTicker<>(SpriteUtil::markSpriteActive);
    }

    // Chunk renderer that applies the vintage pass state.
    private static class ChunkRenderer extends DefaultChunkRenderer {

        // Creates a chunk renderer from its device and render pass configuration.
        public ChunkRenderer(RenderDevice device, RenderPassConfiguration<?> renderPassConfiguration) {
            super(device, renderPassConfiguration, GLStateManagerFogService.INSTANCE);
        }

        // Returns whether block face culling is on.
        @Override
        public boolean useBlockFaceCulling(){
            if (com.bdmajora.impetus.umbra.pipeline.UmbraShadowRenderer.isShadowPass()) {
                return false;
            }
            return ImpetusVintage.options().performance.useBlockFaceCulling;
        }

        // Sets the per pass uniforms of a shader interface.
        @Override
        protected void configureShaderInterface(ChunkShaderInterface shader) {
            shader.setTextureSlot(ChunkShaderTextureSlot.BLOCK, 0);
            shader.setTextureSlot(ChunkShaderTextureSlot.LIGHT, 1);
        }
    }
}
