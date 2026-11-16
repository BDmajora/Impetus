package com.bdmajora.extras.mixin.particle;

import com.bdmajora.extras.client.particle.ParticleFilters;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.renderer.RenderGlobal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Mixin that changes RenderGlobal.
@Mixin(RenderGlobal.class)
public class RenderGlobalParticleMixin {
    // Runs at the start of spawnParticle0 and can cancel it.
    @Inject(
            method = "spawnParticle0(IZZDDDDDD[I)Lnet/minecraft/client/particle/Particle;",
            at = @At("HEAD"),
            cancellable = true
    )
    private void impetus$filterParticle(int particleId, boolean ignoreRange, boolean minimal,
                                        // Spawn position.
                                        double x, double y, double z,
                                        // Spawn velocity.
                                        double xSpeed, double ySpeed, double zSpeed,
                                        // Extra particle parameters.
                                        int[] parameters, CallbackInfoReturnable<Particle> cir) {
        if (!ParticleFilters.isAllowed(particleId)) {
            cir.setReturnValue(null);
        }
    }
}
