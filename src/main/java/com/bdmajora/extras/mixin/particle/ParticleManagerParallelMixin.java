package com.bdmajora.extras.mixin.particle;

import com.bdmajora.extras.client.particle.LightCachedParticle;
import com.bdmajora.extras.client.particle.ParticleTicker;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

// Mixin that changes ParticleManager.
@Mixin(ParticleManager.class)
public abstract class ParticleManagerParallelMixin {
    // Reference to the field queue of the target.
    @Shadow
    @Final
    @Mutable
    private Queue<Particle> queue;

    // Reference to the method tick particle of the target.
    @Shadow
    private void tickParticle(Particle particle) {
        throw new AssertionError();
    }

    // Runs before every return of constructor.
    @Inject(method = "<init>", at = @At("RETURN"))
    private void impetus$concurrentQueue(CallbackInfo ci) {
        this.queue = new ConcurrentLinkedQueue<>(this.queue);
    }

    // Runs at the start of updateEffects.
    @Inject(method = "updateEffects", at = @At("HEAD"))
    private void impetus$beginTick(CallbackInfo ci) {
        ParticleTicker.beginTick();
    }

    // Runs at the start of tickParticleList and can cancel it.
    @Inject(method = "tickParticleList", at = @At("HEAD"), cancellable = true)
    private void impetus$tickLayerParallel(Queue<Particle> layer, CallbackInfo ci) {
        if (!ParticleTicker.shouldParallelize(layer)) {
            return;
        }
        ParticleTicker.tickLayer(layer, particle -> this.tickParticle(particle));
        ci.cancel();
    }

    // Runs at the end of tickParticle.
    @Inject(method = "tickParticle", at = @At("TAIL"))
    private void impetus$sampleLight(Particle particle, CallbackInfo ci) {
        if (ParticleTicker.lightCache && particle.isAlive()) {
            ((LightCachedParticle) particle).impetus$sampleLight();
        }
    }
}
