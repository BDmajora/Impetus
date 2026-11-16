package com.bdmajora.extras.mixin.render.entity;

import com.bdmajora.extras.Extras;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.layers.LayerCape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Mixin that changes LayerCape.
@Mixin(LayerCape.class)
public class LayerCapeMixin {
    // Runs at the start of doRenderLayer and can cancel it.
    @Inject(
            method = "doRenderLayer(Lnet/minecraft/client/entity/AbstractClientPlayer;FFFFFFF)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void impetus$doRenderLayer(AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
                                       // Animation inputs of the layer.
                                       float partialTicks, float ageInTicks, float netHeadYaw,
                                       // Head pitch and model scale.
                                       float headPitch, float scale, CallbackInfo ci) {
        if (!Extras.options().detail.showCapes) {
            ci.cancel();
        }
    }
}
