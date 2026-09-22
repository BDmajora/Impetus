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

@Mixin(RenderGlobal.class)
public abstract class RenderGlobalMixin implements SimpleWorldRenderer.Provider<ImpetusWorldRenderer> {

    @Shadow
    @Final
    private Map<Integer, DestroyBlockProgress> damagedBlocks;

    @Shadow @Final private Minecraft mc;
    @Shadow
    @Final
    private RenderManager renderManager;
    @Shadow
    @Final
    private TextureManager renderEngine;
    @Shadow
    private int countEntitiesRendered;
    @Shadow
    private int cloudTickCounter;

    @Shadow
    protected abstract boolean isOutlineActive(Entity entityIn, Entity viewer, ICamera camera);

    @Shadow
    private WorldClient world;
    @Shadow
    @Final
    private Set<TileEntity> setTileEntities;
    private ImpetusWorldRenderer renderer;

    // Zero so vanilla allocates no chunk storage; Impetus owns terrain rendering
    @Redirect(method = "loadRenderers", at = @At(value = "FIELD", target = "Lnet/minecraft/client/settings/GameSettings;renderDistanceChunks:I", ordinal = 1))
    private int nullifyBuiltChunkStorage(GameSettings settings) {
        // Do not allow any resources to be allocated
        return 0;
    }

    // Leaves quality follows the Impetus option rather than the global fancy toggle
    @Redirect(method = "loadRenderers", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/BlockLeaves;setGraphicsLevel(Z)V"))
    private void useConfiguredLeavesGraphicsLevel(BlockLeaves leaves, boolean fancyGraphics) {
        leaves.setGraphicsLevel(ImpetusVintage.options().quality.leavesQuality.isFancy(fancyGraphics));
    }

    // Creates the Impetus renderer alongside vanilla's RenderGlobal
    @Inject(method = "<init>", at = @At("RETURN"))
    private void init(Minecraft minecraft, CallbackInfo ci) {
        this.renderer = new ImpetusWorldRenderer();
    }

    @Override
    public ImpetusWorldRenderer impetus$getWorldRenderer() {
        return this.renderer;
    }

    // Set for the duration of setWorldAndLoadRenderers, which calls loadRenderers internally; without it each world change rebuilt the section manager twice (onReload for the world being left, then onWorldChanged), discarding every mesh and recompiling every program twice, visible as endless unloading on park-hopping servers
    @Unique
    private boolean impetus$changingWorld;

    @Inject(method = "setWorldAndLoadRenderers", at = @At("HEAD"))
    private void impetus$beginWorldChange(WorldClient world, CallbackInfo ci) {
        this.impetus$changingWorld = true;
    }

    // Tears down and rebuilds the renderer for the new world inside a managed-code scope
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

    // Overwrite: from the Impetus renderer, for the debug screen
    @Overwrite
    public int getRenderedChunks() {
        return this.renderer.getVisibleChunkCount();
    }

    // Overwrite: whether the Impetus build queue is empty
    @Overwrite
    public boolean hasNoChunkUpdates() {
        return this.renderer.isTerrainRenderComplete();
    }

    // Forwards vanilla's update request to the Impetus renderer
    @Inject(method = "setDisplayListEntitiesDirty", at = @At("RETURN"))
    private void onTerrainUpdateScheduled(CallbackInfo ci) {
        this.renderer.scheduleTerrainUpdate();
    }

    // Overwrite: draws the layer through Impetus and returns zero, since vanilla's count is meaningless here
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
        }

        this.mc.entityRenderer.disableLightmap();

        return 1;
    }

    // Overwrite: runs the Impetus visibility update in place of vanilla's chunk graph walk
    @Overwrite
    public void setupTerrain(Entity entity, double tick, ICamera camera, int frame, boolean spectator) {
        RenderDevice.enterManagedCode();

        try {
            // `frustum.culling = false`: the pack wants off-screen geometry drawn too, so the frustum test is replaced with one that accepts everything (the shadow pass's trick)
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

    // Same viewport, but with a frustum that accepts every section (frustum.culling = false).
    @Unique
    private static Viewport unculledViewport(
            Viewport source) {
        var transform = source.getTransform();
        return new Viewport(
                (minX, minY, minZ, maxX, maxY, maxZ) -> true,
                new Vector3d(transform.x, transform.y, transform.z));
    }

    // Overwrite: schedules Impetus rebuilds for every section the box touches
    @Overwrite
    private void markBlocksForUpdate(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, boolean important) {
        this.renderer.scheduleRebuildForBlockArea(minX, minY, minZ, maxX, maxY, maxZ, important);
    }

    // The following two redirects force light updates to trigger chunk updates without checking vanilla's chunk renderer flags
    @Redirect(method = "updateClouds", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/chunk/ChunkRenderDispatcher;hasNoFreeRenderBuilders()Z"))
    private boolean alwaysHaveBuilders(ChunkRenderDispatcher instance) {
        return false;
    }

    // Vanilla's chunk task set is always empty since Impetus schedules its own
    @Redirect(method = "updateClouds", at = @At(value = "INVOKE", target = "Ljava/util/Set;isEmpty()Z", ordinal = 1))
    private boolean alwaysHaveNoTasks(Set instance) {
        return true;
    }

    // Takes over both cloud modes with SodiumCloudRenderer's face-culled mesh (see that class for why vanilla's depth-prepass mesh cannot survive a shader pipeline); the pack's clouds directive is already folded into shouldRenderClouds() by GameSettingsCloudsMixin
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
                // A mod owning this dimension's clouds gets vanilla's dispatch, including the Forge render handler that runs ahead of any cloud geometry
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

    // The vanilla fallback honours the cloud-height option but not the distance options: its fancy mesh emits walls under hardcoded l2 guards relative to its own -3..4 range, so widening the range only multiplies wall count; the Sodium path owns the distance slider
    @Redirect(method = "renderClouds", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/WorldProvider;getCloudHeight()F"))
    private float getConfiguredFastCloudHeight(WorldProvider provider) {
        return getConfiguredCloudHeight(provider);
    }

    // Fancy path shares the configured height
    @Redirect(method = "renderCloudsFancy", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/WorldProvider;getCloudHeight()F"))
    private float getConfiguredFancyCloudHeight(WorldProvider provider) {
        return getConfiguredCloudHeight(provider);
    }

    // Cloud height comes from the Impetus option instead of the provider
    private float getConfiguredCloudHeight(WorldProvider provider) {
        return ImpetusVintage.options().quality.cloudHeight;
    }

    // Cloud radius in cells, clamped between vanilla's own extent (32 cells either side) and the cloud projection's far plane (farPlaneDistance * 4); the user's block distance is divided by the *effective* cell size so raising the scale makes cells bigger, not the layer further out
    @Unique
    private int impetus$cloudRadiusCells(float cellSize) {
        int requested = Math.max(8, ImpetusVintage.options().quality.cloudDistance) * 16;
        int farPlane = this.mc.gameSettings.renderDistanceChunks * 16 * 4;
        return Math.max(32, (int) Math.ceil(Math.min(requested, farPlane) / (double) cellSize));
    }

    // Skipped mid world change, since onWorldChanged is about to rebuild everything anyway
    @Inject(method = "loadRenderers", at = @At("RETURN"))
    private void onReload(CallbackInfo ci) {
        // Mid-world-change this reload is for the world being left and onWorldChanged is about to rebuild for the incoming one, so doing it here only discards every chunk mesh an extra time
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

    @Inject(method = "renderEntities", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderHelper;enableStandardItemLighting()V", shift = At.Shift.AFTER, ordinal = 1), cancellable = true)
    public void impetus$renderTileEntities(Entity entity, ICamera camera, float partialTicks, CallbackInfo ci, @Local(ordinal = 0) int pass) {
        this.renderer.renderBlockEntities(new ImpetusWorldRenderer.TileEntityRenderContext(damagedBlocks, partialTicks));

        // setTileEntities is normally empty since vanilla chunk rendering is suppressed, but some mods (Fairy Lights) inject a custom renderer into it, so render any TE found there
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

    // Overwrite: the C: line from the Impetus renderer
    @Overwrite
    public String getDebugInfoRenders() {
        return this.renderer.getChunksDebugString();
    }

    private final EntityGatherer impetus$entityGatherer = new EntityGatherer();

    private List<Entity>[] impetus$collectedEntities;

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
            // These are entities even when the phase falls back to gbuffers_textured_lit, which this pipeline also uses for particles; state the stage explicitly or a pack reading renderStage is told "particles"
            pipeline.setPhase(pipeline.getTranslucentEntityPhase(), 11); // MC_RENDER_STAGE_ENTITIES
        }
        EntityPlayerSP player = this.mc.player;
        BlockPos.MutableBlockPos entityBlockPos = new BlockPos.MutableBlockPos();
        // Apply entity distance scaling
        Entity.setRenderDistanceWeight(MathHelper.clamp((double)this.mc.gameSettings.renderDistanceChunks / 8.0D, 1.0D, 2.5D)
                * (ImpetusVintage.options().quality.entityDistance / 100.0D));

        boolean isSleeping = renderViewEntity instanceof EntityLivingBase && ((EntityLivingBase) renderViewEntity).isPlayerSleeping();

        for(Entity entity : impetus$collectedEntities[pass]) {
            boolean isPlayerAttachedEntity = player != null && entity.isRidingOrBeingRiddenBy(player);
            boolean isLocalPlayerBody = player != null && entity == player
                    && (this.mc.gameSettings.thirdPersonView != 0 || isSleeping);

            // Do regular vanilla checks for visibility
            if(!isLocalPlayerBody
                    && !this.renderManager.shouldRender(entity, camera, renderViewX, renderViewY, renderViewZ)
                    && !isPlayerAttachedEntity) {
                continue;
            }

            // Check if any corners of the bounding box are in a visible subchunk
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
