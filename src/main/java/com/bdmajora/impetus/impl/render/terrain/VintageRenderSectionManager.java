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

import java.util.List;

public class VintageRenderSectionManager extends RenderSectionManager {
    private final WorldClient world;
    @Getter
    private final ClonedChunkSectionCache sectionCache;

    public VintageRenderSectionManager(RenderPassConfiguration<?> configuration, WorldClient world, int renderDistance, CommandList commandList, int minSection, int maxSection) {
        super(configuration, () -> new VintageChunkBuildContext(world, configuration), ChunkRenderer::new, renderDistance, commandList, minSection, maxSection, ImpetusVintage.options().performance.chunkBuilderThreads, true);
        this.world = world;
        this.sectionCache = new ClonedChunkSectionCache(world);
    }

    // The Umbra shadow pass re-drives this path from the sun; routing it onto the dedicated shadow lists lets it draw every section in range regardless of player-view culling, the stability Complementary's shadow.culling = reversed needs
    @Override
    public boolean isInShadowPass() {
        return com.bdmajora.impetus.umbra.pipeline.UmbraShadowRenderer.isShadowPass();
    }

    // Factory, since the base class needs the vertex type before construction
    public static VintageRenderSectionManager create(ChunkVertexType vertexType, WorldClient world, int renderDistance, CommandList commandList) {
        return new VintageRenderSectionManager(VintageRenderPassConfigurationBuilder.build(vertexType), world, renderDistance, commandList, 0, 16);
    }

    // From the Impetus option
    @Override
    protected AsyncOcclusionMode getAsyncOcclusionMode() {
        return ImpetusVintage.options().performance.asyncOcclusionMode;
    }

    // Always; 1.12.2 has no reason to let the queue grow unbounded
    @Override
    protected boolean shouldRespectUpdateTaskQueueSizeLimit() {
        return true;
    }

    // From the Impetus option
    @Override
    protected boolean useFogOcclusion() {
        return ImpetusVintage.options().performance.useFogOcclusion;
    }

    // Off inside opaque blocks or in spectator, matching vanilla
    @Override
    protected boolean shouldUseOcclusionCulling(Viewport positionedViewport, boolean spectator) {
        if (isInShadowPass()) {
            // Voxelization must see a FRAME-STABLE section set: occlusion culling lets marginal sections blink in and out of the shadow draw, so the pack's colored-lighting floodfill chases a different voxel field every frame and strobes
            return false;
        }
        // `occlusion.culling = false`: the pack needs geometry the player cannot see, since it samples the gbuffer from another angle (reflections, its own shadow logic)
        com.bdmajora.impetus.umbra.pipeline.UmbraRenderingPipeline pipeline =
                com.bdmajora.impetus.umbra.Umbra.getRenderingPipeline();
        if (pipeline != null && pipeline.shouldDisableOcclusionCulling()) {
            return false;
        }
        // A spectator inside an opaque block sees nothing with culling on, so it is switched off there like vanilla does
        if (spectator) {
            var camBlockPos = positionedViewport.getBlockCoord();

            if (this.world.getBlockState(new BlockPos(camBlockPos.x(), camBlockPos.y(), camBlockPos.z())).isOpaqueCube()) {
                return false;
            }
        }

        return Minecraft.getMinecraft().renderChunksMany;
    }

    // Empty sections skip building entirely
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
        // Deliberately not isEmpty(): Fulgor widens that to "nothing worth sending to the client", and the visibility graph wants the narrower question of whether there is any geometry
        return array[y] == Chunk.NULL_BLOCK_STORAGE || FulgorRenderBridge.isEmptyOfBlocks(array[y]);
    }

    @Override
    protected @Nullable ChunkBuilderTask<ChunkBuildOutput> createRebuildTask(RenderSection render, int frame) {
        ChunkRenderContext context = WorldSlice.prepare(this.world, new SectionPos(render.getChunkX(), render.getChunkY(), render.getChunkZ()), this.sectionCache);

        if (context == null) {
            return null;
        }

        return new ChunkBuilderMeshingTask(render, context, frame, this.cameraPosition);
    }

    // From the Impetus option
    @Override
    protected boolean allowImportantRebuilds() {
        return !ImpetusVintage.options().performance.alwaysDeferChunkUpdates;
    }

    // Forwards to the base scheduler with the importance flag
    @Override
    protected void scheduleSectionForRebuild(int x, int y, int z, boolean important) {
        this.sectionCache.invalidate(x, y, z);
        super.scheduleSectionForRebuild(x, y, z, important);
    }

    // Runs the build queue, immediately when requested by a block change
    @Override
    public void updateChunks(boolean updateImmediately) {
        this.sectionCache.cleanup();
        super.updateChunks(updateImmediately);
    }

    // Ridiculous workaround: some mods rely on getRenderBoundingBox's side effects to initialise tile entity state; removable if we use it for TE rendering again
    @SuppressWarnings("unchecked")
    private static void retrieveBBForList(List<?> blockEntities) {
        if (!blockEntities.isEmpty()) {
            for (var be : (List<TileEntity>)blockEntities) {
                be.getRenderBoundingBox();
            }
        }
    }

    // Installs a build result and records its tile entities for the block entity pass
    @Override
    protected boolean updateSectionInfo(RenderSection render, @Nullable BuiltRenderSectionData info) {
        if (info instanceof MinecraftBuiltRenderSectionData<?,?> mcData) {
            retrieveBBForList(mcData.culledBlockEntities);
            retrieveBBForList(mcData.globalBlockEntities);
        }
        return super.updateSectionInfo(render, info);
    }

    @Override
    protected @Nullable SectionTicker createSectionTicker() {
        return new GenericSectionSpriteTicker<>(SpriteUtil::markSpriteActive);
    }

    private static class ChunkRenderer extends DefaultChunkRenderer {

        public ChunkRenderer(RenderDevice device, RenderPassConfiguration<?> renderPassConfiguration) {
            super(device, renderPassConfiguration);
        }

        // Never in the shadow pass (Umbra does the same in MixinDefaultChunkRenderer): getVisibleFaces culls by facing relative to the player's occlusion camera, which from underground drops every upward block top above the player, so the ground never reaches the shadow map and sunlight leaks into caves
        @Override
        public boolean useBlockFaceCulling(){
            if (com.bdmajora.impetus.umbra.pipeline.UmbraShadowRenderer.isShadowPass()) {
                return false;
            }
            return ImpetusVintage.options().performance.useBlockFaceCulling;
        }

        // Uploads fog and texture state before drawing
        @Override
        protected void configureShaderInterface(ChunkShaderInterface shader) {
            shader.setTextureSlot(ChunkShaderTextureSlot.BLOCK, 0);
            shader.setTextureSlot(ChunkShaderTextureSlot.LIGHT, 1);
        }
    }
}
