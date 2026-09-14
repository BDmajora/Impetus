package com.bdmajora.extras.mixin.network;

import com.bdmajora.extras.network.NetworkLimits;
import net.minecraft.network.NetHandlerPlayServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

// "Timed out": the keep-alive window a client must answer within, 15 seconds in vanilla, which a long client-side stall (a big chunk batch, a GC pause) can miss
@Mixin(NetHandlerPlayServer.class)
public abstract class NetHandlerPlayServerMixin {
    @ModifyConstant(method = "update", constant = @Constant(longValue = 15000L))
    private long impetus$keepAliveTimeout(long vanilla) {
        return NetworkLimits.keepAliveTimeoutMillis();
    }
}
