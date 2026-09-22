package com.bdmajora.impetus.mixin.core.shader;

import com.bdmajora.impetus.umbra.Umbra;
import net.minecraft.client.renderer.entity.layers.LayerSpiderEyes;
import net.minecraft.entity.monster.EntitySpider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Routes the spider's glowing-eyes overlay through gbuffers_spidereyes (see UmbraRenderingPipeline#beginEyes, otherwise the fullbright lightmap sentinel blooms); anchored on vanilla's color(1,1,1,1) right before the model draw so the program's blend override and lightmap fix win, same shape for all three eyes layers
@Mixin(LayerSpiderEyes.class)
public class LayerSpiderEyesMixin {
    @Inject(method = "doRenderLayer(Lnet/minecraft/entity/monster/EntitySpider;FFFFFFF)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/GlStateManager;color(FFFF)V",
                    shift = At.Shift.AFTER),
            require = 0)
    private void impetus$beginEyes(EntitySpider entity, float limbSwing, float limbSwingAmount, float partialTicks,
                                   float ageInTicks, float netHeadYaw, float headPitch, float scale, CallbackInfo ci) {
        Umbra.beginEyes();
    }

    @Inject(method = "doRenderLayer(Lnet/minecraft/entity/monster/EntitySpider;FFFFFFF)V",
            at = @At("RETURN"), require = 0)
    private void impetus$endEyes(EntitySpider entity, float limbSwing, float limbSwingAmount, float partialTicks,
                                 float ageInTicks, float netHeadYaw, float headPitch, float scale, CallbackInfo ci) {
        Umbra.endEyes();
    }
}
