package com.bdmajora.impetus.mixin.core.shader;

import com.bdmajora.impetus.umbra.Umbra;
import net.minecraft.client.renderer.entity.layers.LayerEnderDragonEyes;
import net.minecraft.entity.boss.EntityDragon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// The ender dragon's eyes overlay. Same layer shape and same reason as LayerSpiderEyesMixin.
@Mixin(LayerEnderDragonEyes.class)
public class LayerEnderDragonEyesMixin {
    @Inject(method = "doRenderLayer(Lnet/minecraft/entity/boss/EntityDragon;FFFFFFF)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/GlStateManager;color(FFFF)V",
                    shift = At.Shift.AFTER),
            require = 0)
    private void impetus$beginEyes(EntityDragon entity, float limbSwing, float limbSwingAmount, float partialTicks,
                                   float ageInTicks, float netHeadYaw, float headPitch, float scale, CallbackInfo ci) {
        Umbra.beginEyes();
    }

    @Inject(method = "doRenderLayer(Lnet/minecraft/entity/boss/EntityDragon;FFFFFFF)V",
            at = @At("RETURN"), require = 0)
    private void impetus$endEyes(EntityDragon entity, float limbSwing, float limbSwingAmount, float partialTicks,
                                 float ageInTicks, float netHeadYaw, float headPitch, float scale, CallbackInfo ci) {
        Umbra.endEyes();
    }
}
