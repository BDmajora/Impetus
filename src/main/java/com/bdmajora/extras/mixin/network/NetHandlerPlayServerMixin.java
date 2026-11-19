package com.bdmajora.extras.mixin.network;

import com.bdmajora.extras.network.NetworkLimits;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.network.NetHandlerPlayServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// Mixin that changes NetHandlerPlayServer.
@Mixin(NetHandlerPlayServer.class)
public abstract class NetHandlerPlayServerMixin {
    @ModifyExpressionValue(method = "update", at = @At(value = "CONSTANT", args = "longValue=15000"), require = 0)
    private long impetus$keepAliveTimeout(long timeout) {
        return timeout == 15000L ? NetworkLimits.keepAliveTimeoutMillis() : timeout;
    }
}
