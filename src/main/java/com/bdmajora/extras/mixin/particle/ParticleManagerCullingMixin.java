package com.bdmajora.extras.mixin.particle;

import com.bdmajora.extras.client.particle.ParticleTicker;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
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

// Frustum test per particle before its quad is built (AsyncParticles' particle culling): a frustum is read off the current matrices once per pass, so a shadow pass gets the shadow frustum, and only vanilla particles are tested since a modded one may draw well outside its box
@Mixin(ParticleManager.class)
public abstract class ParticleManagerCullingMixin {
    @Unique
    private Frustum impetus$frustum;

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

    @WrapOperation(method = "renderParticles", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/particle/Particle;renderParticle(Lnet/minecraft/client/renderer/BufferBuilder;Lnet/minecraft/entity/Entity;FFFFFF)V"))
    private void impetus$cullParticle(Particle particle, BufferBuilder buffer, Entity entity, float partialTicks,
                                      float rotationX, float rotationZ, float rotationYZ, float rotationXY, float rotationXZ,
                                      Operation<Void> original) {
        Frustum frustum = this.impetus$frustum;
        if (frustum != null && !ParticleTicker.isModded(particle)) {
            AxisAlignedBB box = particle.getBoundingBox();
            // A margin around the box covers the particle's own size, which the box tracks loosely, and the billboard rotation
            if (!frustum.isBoxInFrustum(box.minX - 1.0D, box.minY - 1.0D, box.minZ - 1.0D, box.maxX + 1.0D, box.maxY + 1.0D, box.maxZ + 1.0D)) {
                return;
            }
        }
        original.call(particle, buffer, entity, partialTicks, rotationX, rotationZ, rotationYZ, rotationXY, rotationXZ);
    }
}
