package com.bdmajora.coarctatio.mixin.client.resources;

import com.bdmajora.coarctatio.client.resources.StacklessFileNotFoundException;
import net.minecraft.client.resources.FallbackResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.io.FileNotFoundException;

// Both "not found" throws in the per-domain manager
@Mixin(FallbackResourceManager.class)
public abstract class FallbackResourceManagerMixin {
    @Redirect(method = {"getResource", "getAllResources"}, at = @At(value = "NEW", target = "(Ljava/lang/String;)Ljava/io/FileNotFoundException;"))
    private FileNotFoundException coarctatio$stackless(String message) {
        return new StacklessFileNotFoundException(message);
    }
}
