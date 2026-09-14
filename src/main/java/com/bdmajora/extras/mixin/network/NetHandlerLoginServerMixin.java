package com.bdmajora.extras.mixin.network;

import com.bdmajora.extras.network.NetworkLimits;
import net.minecraft.server.network.NetHandlerLoginServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

// "Took too long to log in": vanilla allows 600 ticks, which a client loading hundreds of mods' registries can overrun
@Mixin(NetHandlerLoginServer.class)
public abstract class NetHandlerLoginServerMixin {
    @ModifyConstant(method = "update", constant = @Constant(intValue = 600))
    private int impetus$loginTimeout(int vanilla) {
        return NetworkLimits.loginTimeoutTicks();
    }
}
