package com.bdmajora.impetus.umbra.pipeline.shadow;

import com.bdmajora.impetus.engine.impl.render.viewport.frustum.Frustum;
import com.bdmajora.impetus.umbra.pipeline.ShadowContentSettings;
import com.bdmajora.impetus.umbra.pipeline.rendering.GlState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.AxisAlignedBB;
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

// Drawer of entities and block entities into the shadow map.
public final class ShadowCasters {
    // Extra distance kept around the shadow range.
    static final double CULL_MARGIN = 16.0;

    // Programs of the casters.
    private final ShadowCasterPrograms programs;
    // Settings of what the shadow pass draws.
    private final ShadowContentSettings content;
    // Distance within which casters draw.
    private final double cullRange;
    // Vanilla camera over the entity frustum.
    private final FrustumCamera frustumCamera = new FrustumCamera();
    // Matrix buffer.
    private final FloatBuffer matrixBuffer = ByteBuffer.allocateDirect(16 * Float.BYTES)
            .order(ByteOrder.nativeOrder()).asFloatBuffer();

    // Creates a shadow casters from its programs and content and shadow distance.
    public ShadowCasters(ShadowCasterPrograms programs, ShadowContentSettings content, float shadowDistance) {
        this.programs = Objects.requireNonNull(programs, "programs");
        this.content = Objects.requireNonNull(content, "content");
        this.cullRange = shadowDistance + CULL_MARGIN;
    }

    // Returns whether a position is beyond a range of the camera.
    static boolean outsideRange(double x, double z, Vector3d camera, double range) {
        return Math.abs(x - camera.x) > range || Math.abs(z - camera.z) > range;
    }

    // Draws the casters the shadow search and the entity frustum keep.
    public void render(Minecraft mc, ShadowCasterView view, Frustum entityFrustum, Vector3d camera, Matrix4f projection,
                       Matrix4f modelView, float partialTicks) {
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
            renderManager.cacheActiveRenderInfo(world, mc.fontRenderer, viewEntity, mc.pointedEntity, mc.gameSettings,
                    partialTicks);
            renderManager.setRenderPosition(camera.x, camera.y, camera.z);
            TileEntityRendererDispatcher dispatcher = TileEntityRendererDispatcher.instance;
            dispatcher.prepare(world, mc.getTextureManager(), mc.fontRenderer, viewEntity, mc.objectMouseOver,
                    partialTicks);
            GlState.resetVanillaVertexArrayState();
            if (entities) {
                this.programs.bindEntity();
                this.frustumCamera.set(entityFrustum, camera);
                renderEntities(renderManager, view, world.loadedEntityList, viewEntity, camera, partialTicks, players,
                        others);
            }
            if (blockEntities) {
                this.programs.bindBlockEntity();
                renderBlockEntities(dispatcher, view, world, camera, partialTicks);
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

    // Draws the entities of the shadow pass.
    private void renderEntities(RenderManager renderManager, ShadowCasterView view, List<Entity> entities,
                                Entity viewEntity, Vector3d camera, float partialTicks, boolean players,
                                boolean others) {
        for (Entity entity : entities) {
            if (entity.isDead || outsideRange(entity.posX, entity.posZ, camera, this.cullRange)) {
                continue;
            }
            if (entity instanceof EntityPlayer player ? !players || player.isSpectator() : !others) {
                continue;
            }
            // Always casts the camera's own body and what it rides.
            boolean attached = entity == viewEntity || entity.isRidingOrBeingRiddenBy(viewEntity);
            if (!attached && (!renderManager.shouldRender(entity, this.frustumCamera, camera.x, camera.y, camera.z)
                    || !view.isShadowCasterVisible(entity))) {
                continue;
            }
            renderManager.renderEntityStatic(entity, partialTicks, false);
        }
    }

    // Draws the block entities of the sections the shadow search kept.
    private void renderBlockEntities(TileEntityRendererDispatcher dispatcher, ShadowCasterView view, World world,
                                     Vector3d camera, float partialTicks) {
        boolean lightOnly = this.content.shouldRenderLightBlockEntitiesOnly();
        view.forEachShadowBlockEntity(tileEntity -> {
            BlockPos pos = tileEntity.getPos();
            if (outsideRange(pos.getX(), pos.getZ(), camera, this.cullRange)
                    || dispatcher.getRenderer(tileEntity) == null) {
                return;
            }
            if (lightOnly && tileEntity.getBlockType().getLightValue(world.getBlockState(pos), world, pos) <= 0) {
                return;
            }
            dispatcher.render(tileEntity, partialTicks, -1);
        });
    }

    // Loads a matrix into the fixed function stack.
    private void loadMatrix(Matrix4f matrix) {
        matrix.get(this.matrixBuffer);
        GlStateManager.loadIdentity();
        GlStateManager.multMatrix(this.matrixBuffer);
    }

    // Vanilla camera that answers Render.shouldRender from a camera-relative frustum.
    static final class FrustumCamera implements ICamera {
        // Frustum the boxes are tested against.
        private Frustum frustum = ShadowFrustums.NON_CULLING;
        // Camera position the frustum is relative to.
        private double x;
        private double y;
        private double z;

        // Points the camera at a frustum or keeps everything without one.
        void set(Frustum frustum, Vector3d camera) {
            this.frustum = frustum != null ? frustum : ShadowFrustums.NON_CULLING;
            this.x = camera.x;
            this.y = camera.y;
            this.z = camera.z;
        }

        // Returns whether the world space box meets the frustum.
        @Override
        public boolean isBoundingBoxInFrustum(AxisAlignedBB box) {
            // Subtracted in double first so far boxes keep their precision
            return this.frustum.testAab((float) (box.minX - this.x), (float) (box.minY - this.y),
                    (float) (box.minZ - this.z), (float) (box.maxX - this.x), (float) (box.maxY - this.y),
                    (float) (box.maxZ - this.z));
        }

        // Ignores the position.
        @Override
        public void setPosition(double x, double y, double z) {
        }
    }
}
