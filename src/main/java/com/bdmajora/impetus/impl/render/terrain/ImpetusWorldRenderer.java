package com.bdmajora.impetus.impl.render.terrain;

import com.bdmajora.impetus.impl.render.terrain.fog.GLStateManagerFogService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.DestroyBlockProgress;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraftforge.client.MinecraftForgeClient;
import com.bdmajora.impetus.engine.impl.gl.device.CommandList;
import com.bdmajora.impetus.engine.impl.gui.ImpetusGameOptions;
import com.bdmajora.impetus.engine.impl.render.chunk.ChunkRenderMatrices;
import com.bdmajora.impetus.engine.impl.render.chunk.vertex.format.ChunkMeshFormats;
import com.bdmajora.impetus.engine.impl.render.chunk.vertex.format.ChunkVertexType;
import com.bdmajora.impetus.engine.impl.render.chunk.vertex.format.impl.MeshChunkVertex;
import com.bdmajora.impetus.engine.impl.render.mesh.MeshShaderSupport;
import com.bdmajora.impetus.engine.impl.render.mesh.MeshTerrainConfig;
import com.bdmajora.impetus.engine.impl.render.terrain.SimpleWorldRenderer;
import com.bdmajora.impetus.ImpetusVintage;
import com.bdmajora.impetus.mixin.core.terrain.ActiveRenderInfoAccessor;
import com.bdmajora.impetus.umbra.Umbra;
import com.bdmajora.impetus.umbra.pipeline.shadow.ShadowCasterView;
import com.bdmajora.impetus.umbra.terrain.UmbraMeshShading;
import com.bdmajora.impetus.umbra.terrain.UmbraTerrainProgramOverride;
import com.bdmajora.impetus.umbra.vertices.UmbraMeshVertex;

import java.util.*;
import java.util.function.Consumer;

// World renderer of Impetus terrain.
public class ImpetusWorldRenderer extends SimpleWorldRenderer<WorldClient, VintageRenderSectionManager, BlockRenderLayer, TileEntity, ImpetusWorldRenderer.TileEntityRenderContext>
        implements ShadowCasterView {
    // Data of the tile entity render context.
    public record TileEntityRenderContext(Map<Integer, DestroyBlockProgress> damagedBlocks, float partialTicks) {}

    // Returns the renderer and fails when there is none.
    public static ImpetusWorldRenderer instance() {
        return SimpleWorldRenderer.Provider.getWorldRenderer(Minecraft.getMinecraft().renderGlobal);
    }

    // Returns the renderer or null.
    public static ImpetusWorldRenderer instanceNullable() {
        return SimpleWorldRenderer.Provider.getWorldRendererNullable(Minecraft.getMinecraft().renderGlobal);
    }

    // Returns the minimum build height.
    @Override
    public int getMinimumBuildHeight() {
        return 0;
    }

    // Returns the maximum build height.
    @Override
    public int getMaximumBuildHeight() {
        return this.world.getHeight();
    }

    // Returns the effective render distance.
    @Override
    public int getEffectiveRenderDistance() {
        return Minecraft.getMinecraft().gameSettings.renderDistanceChunks;
    }

    // Creates the chunk render matrices.
    @Override
    protected ChunkRenderMatrices createChunkRenderMatrices() {
        if (com.bdmajora.impetus.umbra.pipeline.UmbraShadowRenderer.isShadowPass()) {
            var state = com.bdmajora.impetus.umbra.uniforms.CapturedRenderingState.INSTANCE;
            return new ChunkRenderMatrices(state.getShadowProjection(), state.getShadowModelView());
        }
        return new ChunkRenderMatrices(ActiveRenderInfoAccessor.getProjectionMatrix(), ActiveRenderInfoAccessor.getModelViewMatrix());
    }

    // Mesh shader terrain shading.
    private UmbraMeshShading meshShading;

    // Creates the render section manager.
    @Override
    protected VintageRenderSectionManager createRenderSectionManager(CommandList commandList) {
        int renderDistance = this.getEffectiveRenderDistance();
        this.meshShading = null;

        if (shouldUseMeshTerrain()) {
            var config = MeshTerrainConfig.from(ImpetusVintage.options().meshTerrain, renderDistance);
            boolean shaders = UmbraTerrainProgramOverride.areShadersActive();
            UmbraMeshShading shading = shaders ? new UmbraMeshShading(Umbra.getCurrentPack(), config) : null;

            try {
                var manager = shaders
                        ? VintageRenderSectionManager.create(UmbraMeshVertex.INSTANCE, this.world, renderDistance, commandList, config, shading)
                        : VintageRenderSectionManager.create(MeshChunkVertex.INSTANCE, this.world, renderDistance, commandList, config);
                this.meshShading = shading;
                return manager;
            } catch (RuntimeException e) {
                ImpetusVintage.logger().error("Mesh-shader terrain failed to start; using the standard renderer", e);
                MeshShaderSupport.markBroken(String.valueOf(e.getMessage()));
            }
        }

        return VintageRenderSectionManager.create(chooseVertexType(), this.world, renderDistance, commandList, null);
    }

    // Returns whether a ported pack the mesh backend could not build sends terrain back to OpenGL on the next frame.
    @Override
    protected boolean needsRebuild() {
        return this.meshShading != null && this.meshShading.hasFailed();
    }

    // Returns the debug strings.
    @Override
    public Collection<String> getDebugStrings() {
        List<String> lines = new ArrayList<>();
        VintageRenderSectionManager manager = this.getRenderSectionManager();
        lines.add(terrainBackend(manager != null && manager.getMeshTerrain() != null));
        lines.addAll(super.getDebugStrings());
        return lines;
    }

    // Returns the name of the terrain backend.
    static String terrainBackend(boolean meshTerrain) {
        if (meshTerrain) {
            return "Terrain: Nvidium" + (UmbraTerrainProgramOverride.areShadersActive() ? " (shader pack port)" : "");
        }
        if (ImpetusVintage.options().graphicsApi != ImpetusGameOptions.GraphicsApi.NVIDIUM) {
            return "Terrain: OpenGL";
        }
        String reason;
        if (!MeshShaderSupport.isSupported()) {
            reason = MeshShaderSupport.getUnsupportedReason();
        } else if (UmbraTerrainProgramOverride.areShadersActive()) {
            reason = UmbraMeshShading.whyNot(Umbra.getCurrentPack());
        } else {
            reason = "";
        }
        return "Terrain: OpenGL, Nvidium off: " + (reason.isEmpty() ? "waiting for the renderer to reload" : reason);
    }

    // Returns whether use mesh terrain.
    static boolean shouldUseMeshTerrain() {
        return ImpetusVintage.options().graphicsApi == ImpetusGameOptions.GraphicsApi.NVIDIUM
                && MeshShaderSupport.isSupported()
                && (!UmbraTerrainProgramOverride.areShadersActive() || UmbraMeshShading.canRun(Umbra.getCurrentPack()));
    }

    // Draws one block layer.
    public void drawChunkLayer(BlockRenderLayer renderLayer, double x, double y, double z) {
        int stage = switch (renderLayer) {
            case SOLID -> 8;
            case CUTOUT_MIPPED -> 9;
            case CUTOUT -> 10;
            case TRANSLUCENT -> 17;
        };
        com.bdmajora.impetus.umbra.uniforms.CapturedRenderingState.INSTANCE.setRenderStage(stage);
        try {
            super.drawChunkLayer(renderLayer, x, y, z);
        } finally {
            com.bdmajora.impetus.umbra.uniforms.CapturedRenderingState.INSTANCE.setRenderStage(0);
        }

        GlStateManager.resetColor();
    }

    // Returns the camera state of a frame.
    public static CameraState captureCameraState(double ticks) {
        Entity viewEntity = Objects.requireNonNull(Minecraft.getMinecraft().getRenderViewEntity(), "Client must have view entity");

        double x = viewEntity.lastTickPosX + (viewEntity.posX - viewEntity.lastTickPosX) * ticks;
        double y = viewEntity.lastTickPosY + (viewEntity.posY - viewEntity.lastTickPosY) * ticks + (double) viewEntity.getEyeHeight();
        double z = viewEntity.lastTickPosZ + (viewEntity.posZ - viewEntity.lastTickPosZ) * ticks;

        float pitch = viewEntity.rotationPitch;
        float yaw = viewEntity.rotationYaw;
        float fogDistance = GLStateManagerFogService.INSTANCE.getFogCutoff();

        return new CameraState(x, y, z, pitch, yaw, fogDistance);
    }

    // Draws a list of block entities.
    @Override
    protected void renderBlockEntityList(List<TileEntity> list, TileEntityRenderContext tileEntityRenderContext) {
        int pass = MinecraftForgeClient.getRenderPass();
        float partialTicks = tileEntityRenderContext.partialTicks;

        for (TileEntity tileEntity : list) {
            if(!tileEntity.shouldRenderInPass(pass))
                continue;

            try {
                TileEntityRendererDispatcher.instance.render(tileEntity, partialTicks, -1);
            } catch(RuntimeException e) {
                if(tileEntity.isInvalid()) {
                    ImpetusVintage.logger().error("Suppressing crash from invalid tile entity", e);
                } else {
                    throw e;
                }
            }
        }
    }

    // Draws the visible block entities and returns the count.
    @Override
    public int renderBlockEntities(TileEntityRenderContext tileEntityRenderContext) {
        int pass = MinecraftForgeClient.getRenderPass();
        TileEntityRendererDispatcher.instance.preDrawBatch();
        int count = super.renderBlockEntities(tileEntityRenderContext);
        TileEntityRendererDispatcher.instance.drawBatch(pass);
        return count;
    }

    // Returns whether an entity's section was visible in the last terrain search.
    public boolean isEntityVisible(Entity entity) {
        if (!ImpetusVintage.options().performance.useEntityCulling || this.renderSectionManager.isInShadowPass()) {
            return true;
        }

        if (entity.isGlowing() || entity.getAlwaysRenderNameTagForRender()) {
            return true;
        }

        AxisAlignedBB box = entity.getRenderBoundingBox();

        return this.isBoxVisible(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
    }

    // Returns whether the entity overlaps a section the shadow search kept.
    @Override
    public boolean isShadowCasterVisible(Entity entity) {
        if (!ImpetusVintage.options().performance.useEntityCulling) {
            return true;
        }

        // Reads the entity's render box.
        AxisAlignedBB box = entity.getRenderBoundingBox();
        return this.isBoxVisible(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
    }

    // Visits the block entities of the sections the shadow search kept.
    @Override
    public void forEachShadowBlockEntity(Consumer<TileEntity> visitor) {
        this.forEachVisibleBlockEntity(visitor);
    }

    // Returns the vertex type for the current pipeline.
    private ChunkVertexType chooseVertexType() {
        if (com.bdmajora.impetus.umbra.terrain.UmbraTerrainProgramOverride.areShadersActive()) {
            return com.bdmajora.impetus.umbra.vertices.UmbraChunkVertexType.INSTANCE;
        }

        if (!ImpetusVintage.options().performance.useCompactVertexFormat) {
            return ChunkMeshFormats.VANILLA_LIKE;
        }

        return ChunkMeshFormats.COMPACT;
    }
}
