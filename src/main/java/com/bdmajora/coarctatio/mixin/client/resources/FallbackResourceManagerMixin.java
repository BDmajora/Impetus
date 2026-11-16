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

// Mixin that changes FallbackResourceManager.
@Mixin(FallbackResourceManager.class)
public abstract class FallbackResourceManagerMixin {
    // Replaces the missing resource exception with one without a stack trace.
    @Redirect(method = {"getResource", "getAllResources"}, at = @At(value = "NEW", target = "(Ljava/lang/String;)Ljava/io/FileNotFoundException;", remap = false))
    private FileNotFoundException coarctatio$stackless(String message) {
        return new StacklessFileNotFoundException(message);
    }

    // Wraps the call of Logger.isDebugEnabled in getInputStream.
    @WrapOperation(method = "getInputStream(Lnet/minecraft/util/ResourceLocation;Lnet/minecraft/client/resources/IResourcePack;)Ljava/io/InputStream;",
            at = @At(value = "INVOKE", target = "Lorg/apache/logging/log4j/Logger;isDebugEnabled()Z", remap = false))
    private boolean coarctatio$untrackedStreams(Logger logger, Operation<Boolean> original) {
        return false;
    }
}
