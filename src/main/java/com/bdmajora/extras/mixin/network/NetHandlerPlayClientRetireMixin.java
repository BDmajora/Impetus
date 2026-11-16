package com.bdmajora.extras.mixin.network;

import com.bdmajora.extras.network.RetiredNetHandler;
import net.minecraft.client.network.NetHandlerPlayClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Mixin that changes NetHandlerPlayClient.
@Mixin(NetHandlerPlayClient.class)
public abstract class NetHandlerPlayClientRetireMixin implements RetiredNetHandler {
    // Field retired added to the target.
    @Unique
    private boolean impetus$retired;

    // Runs at the start of cleanup.
    @Inject(method = "cleanup()V", at = @At("HEAD"))
    private void impetus$retire(CallbackInfo ci) {
        this.impetus$retired = true;
    }

    // Returns whether retired.
    @Override
    public boolean impetus$isRetired() {
        return this.impetus$retired;
    }
}
