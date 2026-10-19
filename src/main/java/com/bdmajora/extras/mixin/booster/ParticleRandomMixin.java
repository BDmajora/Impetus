package com.bdmajora.extras.mixin.booster;

import com.bdmajora.extras.client.booster.FastRandom;
import net.minecraft.client.particle.Particle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Random;

// Every particle constructs its own java.util.Random, the single most expensive line of a particle spawn; under GPU Booster they all share the client thread's FastRandom and the spawn allocates nothing for it
@Mixin(Particle.class)
public class ParticleRandomMixin {
    @Redirect(
            method = "<init>(Lnet/minecraft/world/World;DDD)V",
            at = @At(value = "NEW", target = "java/util/Random", remap = false)
    )
    private Random impetus$sharedRandom() {
        return FastRandom.enabled ? FastRandom.particles() : new Random();
    }
}
