package com.bdmajora.impetus.umbra.pipeline.shadow;

import com.bdmajora.impetus.umbra.pipeline.ShadowContentSettings;
import com.bdmajora.impetus.umbra.pipeline.rendering.GlState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.lwjgl.opengl.GL11;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.List;
import java.util.Objects;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// Draws entities and block entities into the shadow map through the fixed function matrix stack
public final class ShadowCasters {
    // Margin in blocks added to the shadow distance for the horizontal caster cull
    static final double CULL_MARGIN = 16.0;

    // Programs that casters draw with
    private final ShadowCasterPrograms programs;
    // Switches for entities and players and block entities
    private final ShadowContentSettings content;
    // Horizontal distance from the camera along X or Z past which a caster is skipped
    private final double cullRange;
    // Direct buffer that carries one matrix into the fixed function stack
    private final FloatBuffer matrixBuffer = ByteBuffer.allocateDirect(16 * Float.BYTES)
            .order(ByteOrder.nativeOrder()).asFloatBuffer();

    // Creates the caster renderer for a shadow distance in blocks
    public ShadowCasters(ShadowCasterPrograms programs, ShadowContentSettings content, float shadowDistance) {
        this.programs = Objects.requireNonNull(programs, "programs");
        this.content = Objects.requireNonNull(content, "content");
        this.cullRange = shadowDistance + CULL_MARGIN;
    }

    // Returns whether a horizontal position lies farther than the range from the camera along X or Z
    static boolean outsideRange(double x, double z, Vector3d camera, double range) {
        return Math.abs(x - camera.x) > range || Math.abs(z - camera.z) > range;
    }

    // Draws every enabled caster in range under the shadow matrices and restores the matrix stack and depth state
    public void render(Minecraft mc, Vector3d camera, Matrix4f projection, Matrix4f modelView, float partialTicks) {
        boolean players = this.content.shouldRenderPlayer();
        boolean others = this.content.shouldRenderEntities();
        boolean entities = (players || others) && this.programs.hasEntityProgram();
        boolean blockEntities = this.content.shouldRenderAnyBlockEntities() && this.programs.hasBlockEntityProgram();
        if (!entities && !blockEntities) {
            return;
        }
        World world = mc.world;
        Entity viewEntity = mc.getRenderViewEntity();
        if (world == null || viewEntity == null) {
            return;
        }

        GlStateManager.matrixMode(GL11.GL_PROJECTION);
        GlStateManager.pushMatrix();
        loadMatrix(projection);
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.pushMatrix();
        loadMatrix(modelView);
        try {
            RenderManager renderManager = mc.getRenderManager();
            // Both dispatchers take their render origin from the interpolated camera that the model view centres on
            renderManager.cacheActiveRenderInfo(world, mc.fontRenderer, viewEntity, mc.pointedEntity, mc.gameSettings,
                    partialTicks);
            renderManager.setRenderPosition(camera.x, camera.y, camera.z);
            TileEntityRendererDispatcher dispatcher = TileEntityRendererDispatcher.instance;
            dispatcher.prepare(world, mc.getTextureManager(), mc.fontRenderer, viewEntity, mc.objectMouseOver,
                    partialTicks);
            GlState.resetVanillaVertexArrayState();
            if (entities) {
                this.programs.bindEntity();
                renderEntities(renderManager, world.loadedEntityList, camera, partialTicks, players, others);
            }
            if (blockEntities) {
                this.programs.bindBlockEntity();
                renderBlockEntities(dispatcher, world, camera, partialTicks);
            }
        } finally {
            LWJGL.glUseProgram(0);
            GlStateManager.matrixMode(GL11.GL_PROJECTION);
            GlStateManager.popMatrix();
            GlStateManager.matrixMode(GL11.GL_MODELVIEW);
            GlStateManager.popMatrix();
            GlStateManager.disableBlend();
            GlStateManager.enableDepth();
            GlStateManager.depthMask(true);
        }
    }

    // Draws each living entity in range whose kind is enabled
    private void renderEntities(RenderManager renderManager, List<Entity> entities, Vector3d camera,
                                float partialTicks, boolean players, boolean others) {
        for (Entity entity : entities) {
            if (entity.isDead || outsideRange(entity.posX, entity.posZ, camera, this.cullRange)) {
                continue;
            }
            if (entity instanceof EntityPlayer ? !players : !others) {
                continue;
            }
            renderManager.renderEntityStatic(entity, partialTicks, false);
        }
    }

    // Draws each block entity in range that has a renderer and emits light when only emitters are enabled
    private void renderBlockEntities(TileEntityRendererDispatcher dispatcher, World world, Vector3d camera,
                                     float partialTicks) {
        boolean lightOnly = this.content.shouldRenderLightBlockEntitiesOnly();
        for (TileEntity tileEntity : world.loadedTileEntityList) {
            BlockPos pos = tileEntity.getPos();
            if (outsideRange(pos.getX(), pos.getZ(), camera, this.cullRange)
                    || dispatcher.getRenderer(tileEntity) == null) {
                continue;
            }
            if (lightOnly && tileEntity.getBlockType().getLightValue(world.getBlockState(pos), world, pos) <= 0) {
                continue;
            }
            dispatcher.render(tileEntity, partialTicks, -1);
        }
    }

    // Replaces the current fixed function matrix with a JOML matrix
    private void loadMatrix(Matrix4f matrix) {
        matrix.get(this.matrixBuffer);
        GlStateManager.loadIdentity();
        GlStateManager.multMatrix(this.matrixBuffer);
    }
}
