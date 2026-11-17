package com.bdmajora.extras.mixin.particle;

import com.bdmajora.extras.Extras;
import com.bdmajora.extras.ExtrasConfig;
import com.llamalad7.mixinextras.injector.WrapWithCondition;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.util.EnumParticleTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// Mixin that changes EntityRenderer.
@Mixin(EntityRenderer.class)
public class EntityRendererRainSplashMixin {
    // Skips the rain splash particle spawns.
    @WrapWithCondition(method = "addRainParticles()V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/WorldClient;spawnParticle(Lnet/minecraft/util/EnumParticleTypes;DDDDDD[I)V"))
    private boolean impetus$skipRainSplash(WorldClient world, EnumParticleTypes type, double x, double y, double z,
                                           double xSpeed, double ySpeed, double zSpeed, int[] parameters) {
        ExtrasConfig.ParticleSettings settings = Extras.options().particle;
        return settings.all && settings.rainSplash;
    }
}
