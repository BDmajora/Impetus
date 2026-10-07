package com.bdmajora.extras.mixin.network;

import com.bdmajora.extras.network.RetiredNetHandler;
import net.minecraft.client.network.NetHandlerPlayClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// cleanup() nulls the handler's world and is called only from Minecraft.loadWorld(null) on disconnect, so it marks the moment every packet still queued for this connection would throw (PacketThreadUtilMixin drops them). Written and read on the client thread
@Mixin(NetHandlerPlayClient.class)
public abstract class NetHandlerPlayClientRetireMixin implements RetiredNetHandler {
    @Unique
    private boolean impetus$retired;

    @Inject(method = "cleanup()V", at = @At("HEAD"))
    private void impetus$retire(CallbackInfo ci) {
        this.impetus$retired = true;
    }

    @Override
    public boolean impetus$isRetired() {
        return this.impetus$retired;
    }
}
