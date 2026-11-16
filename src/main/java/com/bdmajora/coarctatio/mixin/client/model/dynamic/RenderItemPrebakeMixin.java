package com.bdmajora.coarctatio.mixin.client.model.dynamic;

import com.bdmajora.coarctatio.client.model.dynamic.ItemModelPrebake;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.client.resources.IResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Mixin that changes RenderItem.
@Mixin(RenderItem.class)
public abstract class RenderItemPrebakeMixin {
    // Runs at the start of onResourceManagerReload.
    @Inject(method = "onResourceManagerReload", at = @At("HEAD"))
    private void coarctatio$prebakeItems(IResourceManager resourceManager, CallbackInfo ci) {
        ItemModelPrebake.restart();
    }
}
