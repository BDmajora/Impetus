package com.bdmajora.extras.mixin.network;

import com.bdmajora.extras.network.NetworkLimits;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.server.network.NetHandlerLoginServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// Mixin that changes NetHandlerLoginServer.
@Mixin(NetHandlerLoginServer.class)
public abstract class NetHandlerLoginServerMixin {
    @ModifyExpressionValue(method = "update", at = @At(value = "CONSTANT", args = "intValue=600"), require = 0)
    private int impetus$loginTimeout(int timeout) {
        return timeout == 600 ? NetworkLimits.loginTimeoutTicks() : timeout;
    }
}
