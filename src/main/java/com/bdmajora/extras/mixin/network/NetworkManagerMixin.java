package com.bdmajora.extras.mixin.network;

import com.bdmajora.extras.network.NetworkLimits;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.timeout.ReadTimeoutHandler;
import net.minecraft.network.NetworkManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// "Connection timeout" while a heavy pack finishes joining: the 30 second read timeout is installed by the channel initialiser on both sides, so it is swapped here once the channel is up rather than by targeting an anonymous class whose number the obfuscation may not preserve
@Mixin(NetworkManager.class)
public abstract class NetworkManagerMixin {
    @Inject(method = "channelActive", at = @At("RETURN"))
    private void impetus$widenReadTimeout(ChannelHandlerContext context, CallbackInfo ci) {
        ChannelPipeline pipeline = context.channel().pipeline();
        if (pipeline.get("timeout") == null) {
            return;
        }
        int seconds = NetworkLimits.readTimeoutSeconds();
        if (seconds <= 0) {
            return;
        }
        pipeline.replace("timeout", "timeout", new ReadTimeoutHandler(seconds));
    }
}
