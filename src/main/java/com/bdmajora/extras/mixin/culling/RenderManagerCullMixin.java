package com.bdmajora.extras.mixin.culling;

import com.bdmajora.extras.client.culling.OcclusionCulling;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// shouldRender is the per-entity gate the world renderer asks before drawing, after the frustum; the local player's body and anything ridden are exempted by the caller, which ORs its own checks in
@Mixin(RenderManager.class)
public abstract class RenderManagerCullMixin {
    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private void impetus$skipOccluded(Entity entity, ICamera camera, double camX, double camY, double camZ, CallbackInfoReturnable<Boolean> cir) {
        if (OcclusionCulling.shouldSkipEntity(entity)) {
            cir.setReturnValue(false);
        }
    }
}
