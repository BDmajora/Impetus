package com.bdmajora.coarctatio.mixin.client;

import com.bdmajora.coarctatio.Coarctatio;
import net.minecraft.client.renderer.block.model.ModelManager;
import net.minecraft.client.resources.IResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Mixin that changes ModelManager.
@Mixin(ModelManager.class)
public class ModelManagerMixin {
    // Runs at the start of onResourceManagerReload.
    @Inject(method = "onResourceManagerReload", at = @At("HEAD"))
    private void coarctatio$openPools(IResourceManager resourceManager, CallbackInfo ci) {
        Coarctatio.onResourceReloadStart();
    }

    // Runs before every return of onResourceManagerReload.
    @Inject(method = "onResourceManagerReload", at = @At("RETURN"))
    private void coarctatio$closePools(IResourceManager resourceManager, CallbackInfo ci) {
        Coarctatio.onResourceReloadFinish();
    }
}
