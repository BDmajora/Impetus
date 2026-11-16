package com.bdmajora.impetus.mixin.core.shader;

import com.bdmajora.impetus.umbra.Umbra;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.renderer.entity.RenderLivingBase;
import net.minecraft.client.renderer.entity.layers.LayerArmorBase;
import net.minecraft.entity.EntityLivingBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Mixin that changes LayerArmorBase.
@Mixin(LayerArmorBase.class)
public class LayerArmorBaseGlintMixin {
    // Runs at the start of renderEnchantedGlint.
    @Inject(method = "renderEnchantedGlint", at = @At("HEAD"), require = 0)
    private static void impetus$beginArmorGlint(RenderLivingBase<?> renderer, EntityLivingBase entity, ModelBase model,
                                                // Limb swing and partial ticks.
                                                float limbSwing, float limbSwingAmount, float partialTicks,
                                                // Age and head angles and scale.
                                                float ageInTicks, float netHeadYaw, float headPitch, float scale,
                                                CallbackInfo ci) {
        Umbra.beginArmorGlint();
    }

    // Runs before every return of renderEnchantedGlint.
    @Inject(method = "renderEnchantedGlint", at = @At("RETURN"), require = 0)
    private static void impetus$endArmorGlint(RenderLivingBase<?> renderer, EntityLivingBase entity, ModelBase model,
                                              // Limb swing and partial ticks.
                                              float limbSwing, float limbSwingAmount, float partialTicks,
                                              // Age and head angles and scale.
                                              float ageInTicks, float netHeadYaw, float headPitch, float scale,
                                              CallbackInfo ci) {
        Umbra.endArmorGlint();
    }
}
