package com.bdmajora.extras.mixin.network;

import com.bdmajora.extras.network.NetworkLimits;
import net.minecraft.server.network.NetHandlerLoginServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

// Mixin that changes NetHandlerLoginServer.
@Mixin(NetHandlerLoginServer.class)
public abstract class NetHandlerLoginServerMixin {
    // Changes a constant in update.
    @ModifyConstant(method = "update", constant = @Constant(intValue = 600))
    private int impetus$loginTimeout(int vanilla) {
        return NetworkLimits.loginTimeoutTicks();
    }
}
