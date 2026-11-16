package com.bdmajora.extras.mixin.booster;

import com.bdmajora.extras.client.booster.FastRandom;
import net.minecraft.client.particle.Particle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Random;

// Mixin that changes Particle.
@Mixin(Particle.class)
public class ParticleRandomMixin {
    // Replaces the creation of Random in constructor.
    @Redirect(
            method = "<init>(Lnet/minecraft/world/World;DDD)V",
            at = @At(value = "NEW", target = "java/util/Random", remap = false)
    )
    private Random impetus$sharedRandom() {
        return FastRandom.enabled ? FastRandom.particles() : new Random();
    }
}
