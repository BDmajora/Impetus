package com.bdmajora.impetus.mixin.core.shader;

import com.bdmajora.impetus.umbra.Umbra;
import net.minecraft.client.renderer.entity.layers.LayerEndermanEyes;
import net.minecraft.entity.monster.EntityEnderman;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Mixin that changes LayerEndermanEyes.
@Mixin(LayerEndermanEyes.class)
public class LayerEndermanEyesMixin {
    // Runs around the call of GlStateManager.color in doRenderLayer.
    @Inject(method = "doRenderLayer(Lnet/minecraft/entity/monster/EntityEnderman;FFFFFFF)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/GlStateManager;color(FFFF)V",
                    shift = At.Shift.AFTER),
            require = 0)
    private void impetus$beginEyes(EntityEnderman entity, float limbSwing, float limbSwingAmount, float partialTicks,
                                   // Age and head angles and scale.
                                   float ageInTicks, float netHeadYaw, float headPitch, float scale, CallbackInfo ci) {
        Umbra.beginEyes();
    }

    // Runs before every return of doRenderLayer.
    @Inject(method = "doRenderLayer(Lnet/minecraft/entity/monster/EntityEnderman;FFFFFFF)V",
            at = @At("RETURN"), require = 0)
    private void impetus$endEyes(EntityEnderman entity, float limbSwing, float limbSwingAmount, float partialTicks,
                                 // Age and head angles and scale.
                                 float ageInTicks, float netHeadYaw, float headPitch, float scale, CallbackInfo ci) {
        Umbra.endEyes();
    }
}
