package com.bdmajora.extras.mixin.render.entity;

import com.bdmajora.extras.client.budget.RenderBudgetController;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Mixin that changes Render.
@Mixin(Render.class)
public abstract class RenderShadowMixin<T extends Entity> {
    // Runs at the start of doRenderShadowAndFire and can cancel it.
    @Inject(
            method = "doRenderShadowAndFire(Lnet/minecraft/entity/Entity;DDDFF)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void impetus$skipShadowOfCulledEntity(Entity entity, double x, double y, double z,
                                                  // Yaw and partial ticks.
                                                  float yaw, float partialTicks, CallbackInfo ci) {
        if (RenderBudgetController.wasCulledThisFrame(entity)) {
            ci.cancel();
        }
    }
}
