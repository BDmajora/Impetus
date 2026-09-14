package com.bdmajora.extras.mixin.threads;

import com.bdmajora.extras.client.ThreadTuning;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Hands the server thread to the tuner when its loop starts, since the getter for it is stripped from the client jar
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerThreadMixin {
    @Inject(method = "run", at = @At("HEAD"))
    private void impetus$announceServerThread(CallbackInfo ci) {
        ThreadTuning.onServerThreadStarted(Thread.currentThread());
    }

    @Inject(method = "run", at = @At("RETURN"))
    private void impetus$forgetServerThread(CallbackInfo ci) {
        ThreadTuning.onServerThreadStopped();
    }
}
