package com.bdmajora.coarctatio.mixin.client.model.dynamic;

import com.bdmajora.coarctatio.client.model.dynamic.ItemModelPrebake;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.client.resources.IResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// RenderItem reloads right after ModelManager, the first point the item locations and the new registry both exist
@Mixin(RenderItem.class)
public abstract class RenderItemPrebakeMixin {
    @Inject(method = "onResourceManagerReload", at = @At("HEAD"))
    private void coarctatio$prebakeItems(IResourceManager resourceManager, CallbackInfo ci) {
        ItemModelPrebake.restart();
    }
}
