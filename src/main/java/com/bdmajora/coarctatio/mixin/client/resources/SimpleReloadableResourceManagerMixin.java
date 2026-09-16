package com.bdmajora.coarctatio.mixin.client.resources;

import com.bdmajora.coarctatio.client.resources.ResourceLookupCaches;
import com.bdmajora.coarctatio.client.resources.StacklessFileNotFoundException;
import net.minecraft.client.resources.SimpleReloadableResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.FileNotFoundException;
import java.util.List;

// The unknown-domain throws, and the reload that bumps the lookup caches' generation before any pack is asked anything
@Mixin(SimpleReloadableResourceManager.class)
public abstract class SimpleReloadableResourceManagerMixin {
    @Redirect(method = {"getResource", "getAllResources"}, at = @At(value = "NEW", target = "(Ljava/lang/String;)Ljava/io/FileNotFoundException;"))
    private FileNotFoundException coarctatio$stackless(String message) {
        return new StacklessFileNotFoundException(message);
    }

    @Inject(method = "reloadResources", at = @At("HEAD"))
    private void coarctatio$newGeneration(List<net.minecraft.client.resources.IResourcePack> packs, CallbackInfo ci) {
        ResourceLookupCaches.onReload();
    }
}
