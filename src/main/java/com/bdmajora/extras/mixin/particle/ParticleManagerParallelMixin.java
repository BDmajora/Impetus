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

// Routes each particle layer through the particle pool and samples light at tick time; the spawn queue is always concurrent since a ticking particle (fireworks, large explosions) spawns more, and mods add effects off-thread regardless
@Mixin(ParticleManager.class)
public abstract class ParticleManagerParallelMixin {
    @Shadow
    @Final
    @Mutable
    private Queue<Particle> queue;

    // Private on the target, so declared private with a placeholder body rather than abstract
    @Shadow
    private void tickParticle(Particle particle) {
        throw new AssertionError();
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void impetus$concurrentQueue(CallbackInfo ci) {
        this.queue = new ConcurrentLinkedQueue<>(this.queue);
    }

    @Inject(method = "updateEffects", at = @At("HEAD"))
    private void impetus$beginTick(CallbackInfo ci) {
        ParticleTicker.beginTick();
    }

    // Small layers keep vanilla's iterator loop; large ones are sliced across the pool with the same per-particle tick and crash handling
    @Inject(method = "tickParticleList", at = @At("HEAD"), cancellable = true)
    private void impetus$tickLayerParallel(Queue<Particle> layer, CallbackInfo ci) {
        if (!ParticleTicker.shouldParallelize(layer)) {
            return;
        }
        ParticleTicker.tickLayer(layer, particle -> this.tickParticle(particle));
        ci.cancel();
    }

    // Sampled after every tick on whichever thread ran it, so the render pass reads a value instead of walking the chunk
    @Inject(method = "tickParticle", at = @At("TAIL"))
    private void impetus$sampleLight(Particle particle, CallbackInfo ci) {
        if (ParticleTicker.lightCache && particle.isAlive()) {
            ((LightCachedParticle) particle).impetus$sampleLight();
        }
    }
}
