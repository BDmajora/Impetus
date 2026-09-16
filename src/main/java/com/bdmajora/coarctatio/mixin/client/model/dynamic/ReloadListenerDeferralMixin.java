package com.bdmajora.coarctatio.mixin.client.model.dynamic;

import com.bdmajora.coarctatio.client.model.dynamic.DynamicModels;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraft.client.resources.SimpleReloadableResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// A listener that has to run before the model reload is kept aside and run from it, instead of in registration order
@Mixin(SimpleReloadableResourceManager.class)
public abstract class ReloadListenerDeferralMixin {
    @Inject(method = "registerReloadListener", at = @At("HEAD"), cancellable = true)
    private void coarctatio$deferListener(IResourceManagerReloadListener listener, CallbackInfo ci) {
        if (DynamicModels.deferListener(listener)) {
            ci.cancel();
        }
    }
}
