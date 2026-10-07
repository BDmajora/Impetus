package com.bdmajora.coarctatio.mixin.client.resources;

import com.bdmajora.coarctatio.client.resources.StacklessFileNotFoundException;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.resources.FallbackResourceManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.io.FileNotFoundException;

// Both "not found" throws in the per-domain manager, and the leak tracker that captures a stack trace for every stream it opens
@Mixin(FallbackResourceManager.class)
public abstract class FallbackResourceManagerMixin {
    @Redirect(method = {"getResource", "getAllResources"}, at = @At(value = "NEW", target = "(Ljava/lang/String;)Ljava/io/FileNotFoundException;"))
    private FileNotFoundException coarctatio$stackless(String message) {
        return new StacklessFileNotFoundException(message);
    }

    // Forge's log config always has debug on, so vanilla wraps every resource stream in a tracker that prints a stack trace and registers a finalizer, whose global lock also serialises the parallel model and texture readers
    @WrapOperation(method = "getInputStream(Lnet/minecraft/util/ResourceLocation;Lnet/minecraft/client/resources/IResourcePack;)Ljava/io/InputStream;",
            at = @At(value = "INVOKE", target = "Lorg/apache/logging/log4j/Logger;isDebugEnabled()Z", remap = false))
    private boolean coarctatio$untrackedStreams(Logger logger, Operation<Boolean> original) {
        return false;
    }
}
