package com.bdmajora.coarctatio.mixin.client.model.dynamic;

import net.minecraftforge.fml.client.FMLClientHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Mixin that changes FMLClientHandler.
@Mixin(value = FMLClientHandler.class, remap = false)
public abstract class MissingTextureLogMixin {
    // Runs at the start of logMissingTextureErrors and can cancel it.
    @Inject(method = "logMissingTextureErrors", at = @At("HEAD"), cancellable = true)
    private void coarctatio$silenceScanNoise(CallbackInfo ci) {
        ci.cancel();
    }
}
