package com.bdmajora.extras.mixin.culling;

import com.bdmajora.extras.client.culling.OcclusionCulling;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Mixin that records the camera of each frame for the occlusion worker.
@Mixin(RenderGlobal.class)
public abstract class RenderGlobalOcclusionMixin {
    // Records the camera point before the entities are drawn.
    @Inject(method = "renderEntities", at = @At("HEAD"))
    private void impetus$captureCamera(Entity renderViewEntity, ICamera camera, float partialTicks, CallbackInfo ci) {
        OcclusionCulling.captureCamera(renderViewEntity, partialTicks);
    }
}
