package com.bdmajora.impetus.mixin.core.shader;

import com.bdmajora.impetus.umbra.Umbra;
import net.minecraft.client.renderer.entity.layers.LayerSpiderEyes;
import net.minecraft.entity.monster.EntitySpider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Mixin that changes LayerSpiderEyes.
@Mixin(LayerSpiderEyes.class)
public class LayerSpiderEyesMixin {
    // Runs around the call of GlStateManager.color in doRenderLayer.
    @Inject(method = "doRenderLayer(Lnet/minecraft/entity/monster/EntitySpider;FFFFFFF)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/GlStateManager;color(FFFF)V",
                    shift = At.Shift.AFTER),
            require = 0)
    private void impetus$beginEyes(EntitySpider entity, float limbSwing, float limbSwingAmount, float partialTicks,
                                   // Age and head angles and scale.
                                   float ageInTicks, float netHeadYaw, float headPitch, float scale, CallbackInfo ci) {
        Umbra.beginEyes();
    }

    // Runs before every return of doRenderLayer.
    @Inject(method = "doRenderLayer(Lnet/minecraft/entity/monster/EntitySpider;FFFFFFF)V",
            at = @At("RETURN"), require = 0)
    private void impetus$endEyes(EntitySpider entity, float limbSwing, float limbSwingAmount, float partialTicks,
                                 // Age and head angles and scale.
                                 float ageInTicks, float netHeadYaw, float headPitch, float scale, CallbackInfo ci) {
        Umbra.endEyes();
    }
}
