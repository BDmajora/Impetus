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

// Mixin that changes SimpleReloadableResourceManager.
@Mixin(SimpleReloadableResourceManager.class)
public abstract class SimpleReloadableResourceManagerMixin {
    // Replaces the missing resource exception with one without a stack trace.
    @Redirect(method = {"getResource", "getAllResources"}, at = @At(value = "NEW", target = "(Ljava/lang/String;)Ljava/io/FileNotFoundException;", remap = false))
    private FileNotFoundException coarctatio$stackless(String message) {
        return new StacklessFileNotFoundException(message);
    }

    // Runs at the start of reloadResources.
    @Inject(method = "reloadResources", at = @At("HEAD"))
    private void coarctatio$newGeneration(List<net.minecraft.client.resources.IResourcePack> packs, CallbackInfo ci) {
        ResourceLookupCaches.onReload();
    }
}
