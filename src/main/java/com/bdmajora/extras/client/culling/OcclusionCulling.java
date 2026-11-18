package com.bdmajora.extras.client.culling;

import com.bdmajora.extras.Extras;
import com.bdmajora.extras.ExtrasConfig;
import com.bdmajora.impetus.umbra.pipeline.UmbraShadowRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.client.MinecraftForgeClient;

import java.util.Locale;

// Skips the drawing and ticking of entities and block entities hidden behind blocks.
public final class OcclusionCulling {
    // Age after which a verdict no longer counts.
    static final long STALE_NANOS = 1_000_000_000L;
    // Squared distance from the eye beyond which the reported camera is not trusted.
    static final double MAX_CAMERA_OFFSET_SQ = 64.0D;
    // Grid changes the main thread queues for the worker.
    static final OcclusionInbox INBOX = new OcclusionInbox();
    // Snapshots the main thread hands to the worker.
    static final OcclusionSnapshot.Exchange EXCHANGE = new OcclusionSnapshot.Exchange();
    // Gatherer of the snapshots.
    static final OcclusionCollector COLLECTOR = new OcclusionCollector(INBOX, EXCHANGE);
    // Camera of the last drawn frame or null before the first one.
    static volatile OcclusionCamera camera;

    // Prevents instantiation
    private OcclusionCulling() {
    }

    // Returns the occlusion settings.
    private static ExtrasConfig.OcclusionSettings settings() {
        return Extras.options().occlusion;
    }

    // Publishes the paused count and the snapshot of this tick.
    public static void tick(Minecraft client) {
        DormantTicks.endTick();
        if (!settings().enabled) {
            COLLECTOR.follow(null);
            return;
        }
        OcclusionCullingThread.ensureRunning();
        COLLECTOR.tick(client, settings());
    }

    // Records the camera point of the frame being drawn.
    public static void captureCamera(Entity view, float partialTicks) {
        if (view == null || UmbraShadowRenderer.isShadowPass() || MinecraftForgeClient.getRenderPass() > 0) {
            return;
        }
        double eyeX = view.lastTickPosX + (view.posX - view.lastTickPosX) * partialTicks;
        double eyeY = view.lastTickPosY + (view.posY - view.lastTickPosY) * partialTicks + view.getEyeHeight();
        double eyeZ = view.lastTickPosZ + (view.posZ - view.lastTickPosZ) * partialTicks;
        Vec3d reported = ActiveRenderInfo.projectViewFromEntity(view, partialTicks);
        if (reported != null) {
            double dx = reported.x - eyeX;
            double dy = reported.y - eyeY;
            double dz = reported.z - eyeZ;
            if (dx * dx + dy * dy + dz * dz <= MAX_CAMERA_OFFSET_SQ) {
                camera = new OcclusionCamera(reported.x, reported.y, reported.z);
                return;
            }
        }
        camera = new OcclusionCamera(eyeX, eyeY, eyeZ);
    }

    // Returns whether the last verdict hid the target and is recent enough to trust.
    static boolean isFreshlyOccluded(Cullable target) {
        return target.impetus$isOccluded() && System.nanoTime() - target.impetus$occlusionStamp() < STALE_NANOS;
    }

    // Returns whether the entity is hidden from the camera.
    public static boolean isHidden(Entity entity) {
        ExtrasConfig.OcclusionSettings settings = settings();
        if (!settings.enabled || !settings.entities || entity.ignoreFrustumCheck || entity.isGlowing()) {
            return false;
        }
        return isFreshlyOccluded((Cullable) entity);
    }

    // Returns whether a hidden entity still draws its name like vanilla does through walls.
    private static boolean keepsName(Entity entity) {
        return settings().nameTagsThroughWalls && entity instanceof EntityLivingBase && entity.getAlwaysRenderNameTagForRender();
    }

    // Returns whether the drawing of the entity is skipped completely.
    public static boolean shouldSkipEntity(Entity entity) {
        return isHidden(entity) && !UmbraShadowRenderer.isShadowPass() && !keepsName(entity);
    }

    // Returns whether only the name of the hidden entity is drawn.
    public static boolean isLabelOnly(Entity entity) {
        return isHidden(entity) && !UmbraShadowRenderer.isShadowPass() && keepsName(entity);
    }

    // Returns whether the drawing of the block entity is skipped.
    public static boolean shouldSkipBlockEntity(TileEntity blockEntity) {
        ExtrasConfig.OcclusionSettings settings = settings();
        if (!settings.enabled || !settings.blockEntities || UmbraShadowRenderer.isShadowPass()) {
            return false;
        }
        return isFreshlyOccluded((Cullable) blockEntity);
    }

    // Returns the overlay line of the last pass.
    public static String status() {
        if (Minecraft.getMinecraft().world == null) {
            return "";
        }
        return String.format(Locale.ROOT, "Occlusion: %d/%d entities %d/%d block entities hidden %d paused %.1f ms",
                OcclusionCullingThread.lastEntitiesHidden, OcclusionCullingThread.lastEntities,
                OcclusionCullingThread.lastBlockEntitiesHidden, OcclusionCullingThread.lastBlockEntities,
                DormantTicks.lastPaused(), OcclusionCullingThread.lastPassNanos / 1_000_000.0D);
    }
}
