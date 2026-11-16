package com.bdmajora.extras.mixin.profiler;

import com.bdmajora.extras.client.ProfilerHelper;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Mixin that changes RenderManager.
@Mixin(RenderManager.class)
public abstract class RenderManagerMixin {
    // Reference to the method get entity render object of the target.
    @Shadow
    public abstract <T extends Entity> Render<T> getEntityRenderObject(Entity entity);

    // Runs at the start of renderEntity.
    @Inject(method = "renderEntity", at = @At("HEAD"))
    private void impetus$beginRenderEntity(Entity entity, double x, double y, double z, float yaw,
                                           // Partial ticks and debug flag.
                                           float partialTicks, boolean debug, CallbackInfo ci) {
        ProfilerHelper.startSection(entity.world, this.getEntityRenderObject(entity));
    }

    // Runs at the end of renderEntity.
    @Inject(method = "renderEntity", at = @At("TAIL"))
    private void impetus$endRenderEntity(Entity entity, double x, double y, double z, float yaw,
                                         // Partial ticks and debug flag.
                                         float partialTicks, boolean debug, CallbackInfo ci) {
        ProfilerHelper.endSection(entity.world, this.getEntityRenderObject(entity));
    }
}
