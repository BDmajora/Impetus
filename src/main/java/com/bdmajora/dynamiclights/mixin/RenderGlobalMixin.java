package com.bdmajora.dynamiclights.mixin;

import com.bdmajora.dynamiclights.DynamicLights;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Mixin that changes RenderGlobal.
@Mixin(RenderGlobal.class)
public abstract class RenderGlobalMixin {
    // Runs at the start of renderEntities.
    @Inject(method = "renderEntities", at = @At("HEAD"))
    private void impetus$updateDynamicLights(Entity renderViewEntity, ICamera camera, float partialTicks,
                                             CallbackInfo ci) {
        DynamicLights.engine().updateAll((RenderGlobal) (Object) this);
    }
}
