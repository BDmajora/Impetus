package com.bdmajora.impetus.mixin.core.terrain;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.block.BlockLeaves;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.WorldProvider;
import com.bdmajora.impetus.ImpetusVintage;
import com.bdmajora.impetus.engine.impl.gl.device.RenderDevice;
import com.bdmajora.impetus.engine.impl.render.terrain.SimpleWorldRenderer;
import com.bdmajora.impetus.engine.impl.render.viewport.ViewportProvider;
import com.bdmajora.impetus.impl.compat.littletiles.LittleTilesCompat;
import com.bdmajora.impetus.impl.render.clouds.SodiumCloudRenderer;
import com.bdmajora.impetus.umbra.Umbra;
import com.bdmajora.impetus.umbra.pipeline.UmbraRenderingPipeline;
import com.bdmajora.impetus.umbra.shaderpack.loading.ProgramId;
import org.spongepowered.asm.mixin.Final;
import com.bdmajora.impetus.engine.impl.render.viewport.Viewport;
import org.joml.Vector3d;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraft.client.renderer.texture.TextureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.bdmajora.impetus.impl.render.entity.EntityGatherer;
import com.bdmajora.impetus.impl.render.terrain.ImpetusWorldRenderer;

import java.util.*;

// Mixin that changes RenderGlobal.
@Mixin(RenderGlobal.class)
public abstract class RenderGlobalMixin implements SimpleWorldRenderer.Provider<ImpetusWorldRenderer> {

    @Shadow
    @Final
    private Map<Integer, DestroyBlockProgress> damagedBlocks;

    // Reference to the field render manager of the target.
    @Shadow @Final private Minecraft mc;
    @Shadow
    @Final
    private RenderManager renderManager;
    // Reference to the field render engine of the target.
    @Shadow
    @Final
    private TextureManager renderEngine;
    // Reference to the field count entities rendered of the target.
    @Shadow
    private int countEntitiesRendered;
    // Reference to the field cloud tick counter of the target.
    @Shadow
    private int cloudTickCounter;

    // Reference to the method is outline active of the target.
    @Shadow
    protected abstract boolean isOutlineActive(Entity entityIn, Entity viewer, ICamera camera);

    // Reference to the field world of the target.
    @Shadow
    private WorldClient world;
    // Reference to the field set tile entities of the target.
    @Shadow
    @Final
    private Set<TileEntity> setTileEntities;
    // Impetus world renderer.
    private ImpetusWorldRenderer renderer;

    // Replaces the field access GameSettings.renderDistanceChunks in loadRenderers.
    @Redirect(method = "loadRenderers", at = @At(value = "FIELD", target = "Lnet/minecraft/client/settings/GameSettings;renderDistanceChunks:I", ordinal = 1))
    private int nullifyBuiltChunkStorage(GameSettings settings) {
        return 0;
    }

    // Replaces the call of BlockLeaves.setGraphicsLevel in loadRenderers.
    @Redirect(method = "loadRenderers", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/BlockLeaves;setGraphicsLevel(Z)V"))
    private void useConfiguredLeavesGraphicsLevel(BlockLeaves leaves, boolean fancyGraphics) {
        leaves.setGraphicsLevel(ImpetusVintage.options().quality.leavesQuality.isFancy(fancyGraphics));
    }

    // Runs before every return of constructor.
    @Inject(method = "<init>", at = @At("RETURN"))
    private void init(Minecraft minecraft, CallbackInfo ci) {
        this.renderer = new ImpetusWorldRenderer();
    }

    // Returns the world renderer.
    @Override
    public ImpetusWorldRenderer impetus$getWorldRenderer() {
        return this.renderer;
    }

    // Field changing world added to the target.
    @Unique
    private boolean impetus$changingWorld;

    // Runs at the start of setWorldAndLoadRenderers.
    @Inject(method = "setWorldAndLoadRenderers", at = @At("HEAD"))
    private void impetus$beginWorldChange(WorldClient world, CallbackInfo ci) {
        this.impetus$changingWorld = true;
        net.minecraft.world.DimensionType type = world == null || world.provider == null ? null : world.provider.getDimensionType();
        Umbra.onWorldChanged(type == null ? 0 : type.getId(), type == null ? null : type.getName());
    }

    // Runs before every return of setWorldAndLoadRenderers.
    @Inject(method = "setWorldAndLoadRenderers", at = @At("RETURN"))
    private void onWorldChanged(WorldClient world, CallbackInfo ci) {
        this.impetus$changingWorld = false;

        RenderDevice.enterManagedCode();

        try {
            this.renderer.setWorld(world);
        } finally {
            RenderDevice.exitManagedCode();
        }
    }

    // Replaces the whole method getRenderedChunks.
    @Overwrite
    public int getRenderedChunks() {
        return this.renderer.getVisibleChunkCount();
    }

    // Replaces the whole method hasNoChunkUpdates.
    @Overwrite
    public boolean hasNoChunkUpdates() {
        return this.renderer.isTerrainRenderComplete();
    }

    // Runs before every return of setDisplayListEntitiesDirty.
    @Inject(method = "setDisplayListEntitiesDirty", at = @At("RETURN"))
    private void onTerrainUpdateScheduled(CallbackInfo ci) {
        this.renderer.scheduleTerrainUpdate();
    }

    // Replaces the whole method renderBlockLayer.
    @Overwrite
    public int renderBlockLayer(BlockRenderLayer blockLayerIn, double partialTicks, int pass, Entity entityIn) {
        RenderDevice.enterManagedCode();

        RenderHelper.disableStandardItemLighting();

        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
        GlStateManager.bindTexture(this.mc.getTextureMapBlocks().getGlTextureId());
        GlStateManager.enableTexture2D();

        this.mc.entityRenderer.enableLightmap();

        double d3 = entityIn.lastTickPosX + (entityIn.posX - entityIn.lastTickPosX) * partialTicks;
        double d4 = entityIn.lastTickPosY + (entityIn.posY - entityIn.lastTickPosY) * partialTicks;
        double d5 = entityIn.lastTickPosZ + (entityIn.posZ - entityIn.lastTickPosZ) * partialTicks;

        try {
            this.renderer.drawChunkLayer(blockLayerIn, d3, d4, d5);
        } finally {
            RenderDevice.exitManagedCode();
            this.mc.entityRenderer.disableLightmap();
        }

        return 1;
    }

    // Replaces the whole method setupTerrain.
    @Overwrite
    public void setupTerrain(Entity entity, double tick, ICamera camera, int frame, boolean spectator) {
        RenderDevice.enterManagedCode();

        try {
            if (LittleTilesCompat.IS_LOADED) {
                LittleTilesCompat.flushRemeshes(this.renderer);
            }
            UmbraRenderingPipeline pipeline = Umbra.getRenderingPipeline();
            Viewport viewport =
                    (pipeline != null && pipeline.shouldDisableFrustumCulling())
                            ? unculledViewport(((ViewportProvider) camera).impetus$createViewport())
                            : ((ViewportProvider) camera).impetus$createViewport();
            this.renderer.setupTerrain(viewport, ImpetusWorldRenderer.captureCameraState(tick),
                    frame, spectator, false);
        } finally {
            RenderDevice.exitManagedCode();
        }
    }

    // Helper method unculled viewport added to the target.
    @Unique
    private static Viewport unculledViewport(
            Viewport source) {
        var transform = source.getTransform();
        return new Viewport(
                (minX, minY, minZ, maxX, maxY, maxZ) -> true,
                new Vector3d(transform.x, transform.y, transform.z));
    }

    // Replaces the whole method markBlocksForUpdate.
    @Overwrite
    private void markBlocksForUpdate(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, boolean important) {
        this.renderer.scheduleRebuildForBlockArea(minX, minY, minZ, maxX, maxY, maxZ, important);
    }

    // Replaces the call of ChunkRenderDispatcher.hasNoFreeRenderBuilders in updateClouds.
    @Redirect(method = "updateClouds", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/chunk/ChunkRenderDispatcher;hasNoFreeRenderBuilders()Z"))
    private boolean alwaysHaveBuilders(ChunkRenderDispatcher instance) {
        return false;
    }

    // Replaces the call of Set.isEmpty in updateClouds.
    @Redirect(method = "updateClouds", at = @At(value = "INVOKE", target = "Ljava/util/Set;isEmpty()Z", ordinal = 1, remap = false))
    private boolean alwaysHaveNoTasks(Set instance) {
        return true;
    }

    // Runs at the start of renderClouds and can cancel it.
    @Inject(method = "renderClouds", at = @At("HEAD"), cancellable = true)
    private void impetus$renderCloudsSodium(float partialTicks, int pass, double x, double y, double z,
            CallbackInfo ci) {
        int mode = this.mc.gameSettings.shouldRenderClouds();
        if (mode == 0) {
            ci.cancel();
            return;
        }

        if (!ImpetusVintage.options().performance.useFasterClouds
                || !this.world.provider.isSurfaceWorld()
                || this.world.provider.getCloudRenderer() != null
                || !SodiumCloudRenderer.isReady(this.mc)) {
            return;
        }

        UmbraRenderingPipeline pipeline = Umbra.getRenderingPipeline();
        if (pipeline != null) {
            pipeline.setPhase(ProgramId.Clouds);
        }

        try {
            float cellSize = com.bdmajora.extras.client.CloudPassState.cellSize();

            if (SodiumCloudRenderer.render(this.mc, this.world, this.renderEngine, this.cloudTickCounter, partialTicks,
                    pass, x, y, z, mode == 2, impetus$cloudRadiusCells(cellSize),
                    ImpetusVintage.options().quality.cloudHeight, cellSize)) {
                ci.cancel();
            }
        } finally {
            if (pipeline != null) {
                pipeline.setPhase(null);
            }
        }
    }

    // Replaces the call of WorldProvider.getCloudHeight in renderClouds.
    @Redirect(method = "renderClouds", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/WorldProvider;getCloudHeight()F"))
    private float getConfiguredFastCloudHeight(WorldProvider provider) {
        return getConfiguredCloudHeight(provider);
    }

    // Replaces the call of WorldProvider.getCloudHeight in renderCloudsFancy.
    @Redirect(method = "renderCloudsFancy", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/WorldProvider;getCloudHeight()F"))
    private float getConfiguredFancyCloudHeight(WorldProvider provider) {
        return getConfiguredCloudHeight(provider);
    }

    // Returns the cloud height of a dimension.
    private float getConfiguredCloudHeight(WorldProvider provider) {
        return ImpetusVintage.options().quality.cloudHeight;
    }

    // Helper method cloud radius cells added to the target.
    @Unique
    private int impetus$cloudRadiusCells(float cellSize) {
        int requested = Math.max(8, ImpetusVintage.options().quality.cloudDistance) * 16;
        int farPlane = this.mc.gameSettings.renderDistanceChunks * 16 * 4;
        return Math.max(32, (int) Math.ceil(Math.min(requested, farPlane) / (double) cellSize));
    }

    // Runs before every return of loadRenderers.
    @Inject(method = "loadRenderers", at = @At("RETURN"))
    private void onReload(CallbackInfo ci) {
        if (this.impetus$changingWorld) {
            return;
        }

        RenderDevice.enterManagedCode();

        try {
            this.renderer.reload();
        } finally {
            RenderDevice.exitManagedCode();
        }
    }

    // Runs around the call of RenderHelper.enableStandardItemLighting in renderEntities and can cancel it.
    @Inject(method = "renderEntities", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderHelper;enableStandardItemLighting()V", shift = At.Shift.AFTER, ordinal = 1), cancellable = true)
    public void impetus$renderTileEntities(Entity entity, ICamera camera, float partialTicks, CallbackInfo ci, @Local(ordinal = 0) int pass) {
        this.renderer.renderBlockEntities(new ImpetusWorldRenderer.TileEntityRenderContext(damagedBlocks, partialTicks));

        synchronized(this.setTileEntities) {
            if (!this.setTileEntities.isEmpty()) {
                TileEntityRendererDispatcher.instance.preDrawBatch();
                for (var te : this.setTileEntities) {
                    if (te.shouldRenderInPass(pass)) {
                        TileEntityRendererDispatcher.instance.render(te, partialTicks, -1);
                    }
                }
                TileEntityRendererDispatcher.instance.drawBatch(pass);
            }
        }

        this.mc.entityRenderer.disableLightmap();
        this.mc.profiler.endSection();
        ci.cancel();
    }

    // Replaces the whole method getDebugInfoRenders.
    @Overwrite
    public String getDebugInfoRenders() {
        return this.renderer.getChunksDebugString();
    }

    // Entity gatherer.
    private final EntityGatherer impetus$entityGatherer = new EntityGatherer();

    // Entities collected this frame.
    private List<Entity>[] impetus$collectedEntities;

    // Runs inside renderEntities.
    @Inject(method = "renderEntities", at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/RenderGlobal;renderInfos:Ljava/util/List;", ordinal = 0))
    private void renderEntities(Entity renderViewEntity, ICamera camera, float partialTicks, CallbackInfo ci,
                                @Local(ordinal = 1) List<Entity> outlineEntityList,
                                @Local(ordinal = 2) List<Entity> multipassEntityList,
                                @Local(ordinal = 0) double renderViewX,
                                @Local(ordinal = 1) double renderViewY,
                                @Local(ordinal = 2) double renderViewZ) {
        int pass = MinecraftForgeClient.getRenderPass();
        if (pass == 0 || impetus$collectedEntities == null) {
            impetus$entityGatherer.clear();
            impetus$collectedEntities = impetus$entityGatherer.getLoadedEntityList(world);
        }
        UmbraRenderingPipeline pipeline = Umbra.getRenderingPipeline();
        if (pipeline != null && pass == 1 && pipeline.isRenderingPostDeferredTranslucents()) {
            pipeline.setPhase(pipeline.getTranslucentEntityPhase(), 11);
        }
        EntityPlayerSP player = this.mc.player;
        BlockPos.MutableBlockPos entityBlockPos = new BlockPos.MutableBlockPos();
        Entity.setRenderDistanceWeight(MathHelper.clamp((double)this.mc.gameSettings.renderDistanceChunks / 8.0D, 1.0D, 2.5D)
                * (ImpetusVintage.options().quality.entityDistance / 100.0D));

        boolean isSleeping = renderViewEntity instanceof EntityLivingBase && ((EntityLivingBase) renderViewEntity).isPlayerSleeping();

        for(Entity entity : impetus$collectedEntities[pass]) {
            boolean isPlayerAttachedEntity = player != null && entity.isRidingOrBeingRiddenBy(player);
            boolean isLocalPlayerBody = player != null && entity == player
                    && (this.mc.gameSettings.thirdPersonView != 0 || isSleeping);

            if(!isLocalPlayerBody
                    && !this.renderManager.shouldRender(entity, camera, renderViewX, renderViewY, renderViewZ)
                    && !isPlayerAttachedEntity) {
                continue;
            }

            if(!isLocalPlayerBody && !isPlayerAttachedEntity
                    && !this.renderer.isEntityVisible(entity)) {
                continue;
            }

            if ((entity != renderViewEntity || this.mc.gameSettings.thirdPersonView != 0 || isSleeping)
                    && (entity.posY < 0.0D || entity.posY >= 256.0D || this.world.isBlockLoaded(entityBlockPos.setPos(entity))))
            {
                ++this.countEntitiesRendered;
                this.renderManager.renderEntityStatic(entity, partialTicks, false);

                if (this.isOutlineActive(entity, renderViewEntity, camera))
                {
                    outlineEntityList.add(entity);
                }

                if (this.renderManager.isRenderMultipass(entity)) {
                    multipassEntityList.add(entity);
                }
            }
        }
    }
}
