package com.bdmajora.extras.mixin.network;

import com.bdmajora.extras.network.NetworkLimits;
import net.minecraft.network.NetHandlerPlayServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

// Mixin that changes NetHandlerPlayServer.
@Mixin(NetHandlerPlayServer.class)
public abstract class NetHandlerPlayServerMixin {
    // Changes a constant in update.
    @ModifyConstant(method = "update", constant = @Constant(longValue = 15000L))
    private long impetus$keepAliveTimeout(long vanilla) {
        return NetworkLimits.keepAliveTimeoutMillis();
    }
}
