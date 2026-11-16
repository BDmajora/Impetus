package com.bdmajora.extras.mixin.async;

import com.bdmajora.extras.async.ParallelProcessor;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Mixin that changes MinecraftServer.
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerParallelMixin {
    // Runs at the start of run.
    @Inject(method = "run", at = @At("HEAD"))
    private void impetus$serverStarting(CallbackInfo ci) {
        ParallelProcessor.onServerStart();
    }

    // Runs at the start of stopServer.
    @Inject(method = "stopServer", at = @At("HEAD"))
    private void impetus$shutdownPool(CallbackInfo ci) {
        ParallelProcessor.shutdown();
    }
}
