package com.bdmajora.impetus.mixin.features.options;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.settings.GameSettings;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
import com.bdmajora.impetus.ImpetusVintage;

// Mixin that changes EntityRenderer.
@Mixin(EntityRenderer.class)
public class MixinEntityRenderer {
    // Replaces the field access GameSettings.fancyGraphics in renderRainSnow.
    @Redirect(method = "renderRainSnow", at = @At(value = "FIELD", target = "Lnet/minecraft/client/settings/GameSettings;fancyGraphics:Z", opcode = Opcodes.GETFIELD))
    private boolean redirectGetFancyWeather(GameSettings instance) {
        return ImpetusVintage.options().quality.weatherQuality.isFancy(Minecraft.getMinecraft().gameSettings.fancyGraphics);
    }

    // Changes a constant in renderRainSnow.
    @ModifyConstant(method = "renderRainSnow", constant = @Constant(intValue = 5))
    private int useConfiguredFastWeatherRadius(int radius) {
        return Math.max(1, ImpetusVintage.options().quality.weatherEffectRadius / 2);
    }

    // Changes a constant in renderRainSnow.
    @ModifyConstant(method = "renderRainSnow", constant = @Constant(intValue = 10))
    private int useConfiguredFancyWeatherRadius(int radius) {
        return Math.max(1, ImpetusVintage.options().quality.weatherEffectRadius);
    }

    // Changes a constant in addRainParticles.
    @ModifyConstant(method = "addRainParticles", constant = @Constant(intValue = 10), require = 0)
    private int useConfiguredRainParticleRadius(int radius) {
        return Math.max(1, ImpetusVintage.options().quality.weatherEffectRadius);
    }
}
