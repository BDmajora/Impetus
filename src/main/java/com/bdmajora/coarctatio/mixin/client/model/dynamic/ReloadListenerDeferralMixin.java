package com.bdmajora.coarctatio.mixin.client.model.dynamic;

import com.bdmajora.coarctatio.client.model.dynamic.DynamicModels;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraft.client.resources.SimpleReloadableResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Mixin that changes SimpleReloadableResourceManager.
@Mixin(SimpleReloadableResourceManager.class)
public abstract class ReloadListenerDeferralMixin {
    // Runs at the start of registerReloadListener and can cancel it.
    @Inject(method = "registerReloadListener", at = @At("HEAD"), cancellable = true)
    @SuppressWarnings("deprecation")
    private void coarctatio$deferListener(IResourceManagerReloadListener listener, CallbackInfo ci) {
        if (DynamicModels.deferListener(listener)) {
            ci.cancel();
        }
    }
}
