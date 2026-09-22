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

// Routes the armour enchantment glint through gbuffers_armor_glint (see UmbraRenderingPipeline#beginArmorGlint); HEAD/RETURN mirror OptiFine's renderEnchantedGlintBegin/End span, and both vanilla call sites go through this public static method
@Mixin(LayerArmorBase.class)
public class LayerArmorBaseGlintMixin {
    @Inject(method = "renderEnchantedGlint", at = @At("HEAD"), require = 0)
    private static void impetus$beginArmorGlint(RenderLivingBase<?> renderer, EntityLivingBase entity, ModelBase model,
                                                float limbSwing, float limbSwingAmount, float partialTicks,
                                                float ageInTicks, float netHeadYaw, float headPitch, float scale,
                                                CallbackInfo ci) {
        Umbra.beginArmorGlint();
    }

    @Inject(method = "renderEnchantedGlint", at = @At("RETURN"), require = 0)
    private static void impetus$endArmorGlint(RenderLivingBase<?> renderer, EntityLivingBase entity, ModelBase model,
                                              float limbSwing, float limbSwingAmount, float partialTicks,
                                              float ageInTicks, float netHeadYaw, float headPitch, float scale,
                                              CallbackInfo ci) {
        Umbra.endArmorGlint();
    }
}
