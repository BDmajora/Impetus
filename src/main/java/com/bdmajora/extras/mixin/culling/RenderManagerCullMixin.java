package com.bdmajora.extras.mixin.culling;

import com.bdmajora.extras.client.culling.OcclusionCulling;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Mixin that changes RenderManager.
@Mixin(RenderManager.class)
public abstract class RenderManagerCullMixin {
    // Runs at the start of shouldRender and can cancel it.
    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private void impetus$skipOccluded(Entity entity, ICamera camera, double camX, double camY, double camZ, CallbackInfoReturnable<Boolean> cir) {
        if (OcclusionCulling.shouldSkipEntity(entity)) {
            cir.setReturnValue(false);
        }
    }
}
