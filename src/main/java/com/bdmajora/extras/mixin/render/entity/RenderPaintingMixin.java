package com.bdmajora.extras.mixin.render.entity;

import com.bdmajora.extras.Extras;
import net.minecraft.client.renderer.entity.RenderPainting;
import net.minecraft.entity.item.EntityPainting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Mixin that changes RenderPainting.
@Mixin(RenderPainting.class)
public class RenderPaintingMixin {
    // Runs at the start of doRender and can cancel it.
    @Inject(
            method = "doRender(Lnet/minecraft/entity/item/EntityPainting;DDDFF)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void impetus$doRender(EntityPainting entity, double x, double y, double z,
                                  // Yaw and partial ticks.
                                  float entityYaw, float partialTicks, CallbackInfo ci) {
        if (!Extras.options().render.paintings) {
            ci.cancel();
        }
    }
}
