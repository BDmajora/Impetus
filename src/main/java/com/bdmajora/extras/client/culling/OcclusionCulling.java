package com.bdmajora.extras.client.culling;

import com.bdmajora.extras.Extras;
import com.bdmajora.extras.ExtrasConfig;
import com.bdmajora.impetus.umbra.pipeline.UmbraShadowRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;

// The render-thread side: reads the worker's verdict and decides whether to honour it. A verdict older than a second is stale (the worker stalled or the world changed under it) and is ignored, and nothing is culled in the shadow pass since a mob behind a wall still casts a shadow across the floor
public final class OcclusionCulling {
    private static final long STALE_NANOS = 1_000_000_000L;

    private OcclusionCulling() {
    }

    public static boolean shouldSkipEntity(Entity entity) {
        ExtrasConfig.OcclusionSettings settings = Extras.options().occlusion;
        if (!settings.enabled || !settings.entities) {
            return false;
        }
        OcclusionCullingThread.ensureRunning();
        if (UmbraShadowRenderer.isShadowPass()) {
            return false;
        }
        Cullable cullable = (Cullable) entity;
        return cullable.impetus$isOccluded() && System.nanoTime() - cullable.impetus$occlusionStamp() < STALE_NANOS;
    }

    public static boolean shouldSkipBlockEntity(TileEntity blockEntity) {
        ExtrasConfig.OcclusionSettings settings = Extras.options().occlusion;
        if (!settings.enabled || !settings.blockEntities) {
            return false;
        }
        OcclusionCullingThread.ensureRunning();
        if (UmbraShadowRenderer.isShadowPass()) {
            return false;
        }
        Cullable cullable = (Cullable) blockEntity;
        return cullable.impetus$isOccluded() && System.nanoTime() - cullable.impetus$occlusionStamp() < STALE_NANOS;
    }

    // Debug overlay line
    public static String status() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null) {
            return "";
        }
        return "Occlusion: E " + OcclusionCullingThread.lastEntitiesCulled + " / BE " + OcclusionCullingThread.lastBlockEntitiesCulled
                + " culled, " + OcclusionCullingThread.lastPassNanos / 1000 + " us";
    }
}
