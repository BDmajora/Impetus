package com.bdmajora.extras.mixin.threads;

import com.bdmajora.extras.client.ThreadTuning;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Mixin that changes MinecraftServer.
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerThreadMixin {
    // Runs at the start of run.
    @Inject(method = "run", at = @At("HEAD"))
    private void impetus$announceServerThread(CallbackInfo ci) {
        ThreadTuning.onServerThreadStarted(Thread.currentThread());
    }

    // Runs before every return of run.
    @Inject(method = "run", at = @At("RETURN"))
    private void impetus$forgetServerThread(CallbackInfo ci) {
        ThreadTuning.onServerThreadStopped();
    }
}
