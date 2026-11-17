package com.bdmajora.extras.mixin.particle;

import com.bdmajora.extras.client.particle.ParticleTicker;
import com.llamalad7.mixinextras.injector.WrapWithCondition;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.AxisAlignedBB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Mixin that changes ParticleManager.
@Mixin(ParticleManager.class)
public abstract class ParticleManagerCullingMixin {
    // Camera frustum of this frame's particle draw.
    @Unique
    private Frustum impetus$frustum;

    // Runs at the start of renderParticles.
    @Inject(method = "renderParticles", at = @At("HEAD"))
    private void impetus$buildFrustum(Entity entity, float partialTicks, CallbackInfo ci) {
        if (!ParticleTicker.cullOffscreen) {
            this.impetus$frustum = null;
            return;
        }
        Frustum frustum = new Frustum();
        frustum.setPosition(
                entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * partialTicks,
                entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * partialTicks,
                entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * partialTicks);
        this.impetus$frustum = frustum;
    }

    // Draws a particle only when it is on screen.
    @WrapWithCondition(method = "renderParticles", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/particle/Particle;renderParticle(Lnet/minecraft/client/renderer/BufferBuilder;Lnet/minecraft/entity/Entity;FFFFFF)V"))
    private boolean impetus$cullParticle(Particle particle, BufferBuilder buffer, Entity entity, float partialTicks,
                                         float rotationX, float rotationZ, float rotationYZ, float rotationXY, float rotationXZ) {
        Frustum frustum = this.impetus$frustum;
        if (frustum == null || ParticleTicker.isModded(particle)) {
            return true;
        }
        AxisAlignedBB box = particle.getBoundingBox();
        return frustum.isBoxInFrustum(box.minX - 1.0D, box.minY - 1.0D, box.minZ - 1.0D, box.maxX + 1.0D, box.maxY + 1.0D, box.maxZ + 1.0D);
    }
}
