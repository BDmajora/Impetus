package com.bdmajora.extras.mixin.async;

import com.bdmajora.extras.async.ParallelProcessor;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Drains and drops the pool when the server stops; the integrated server restarts per world and gets a fresh pool on its first batch
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerParallelMixin {
    @Inject(method = "run", at = @At("HEAD"))
    private void impetus$serverStarting(CallbackInfo ci) {
        ParallelProcessor.onServerStart();
    }

    @Inject(method = "stopServer", at = @At("HEAD"))
    private void impetus$shutdownPool(CallbackInfo ci) {
        ParallelProcessor.shutdown();
    }
}
