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

// Mixin that changes NetworkManager.
@Mixin(NetworkManager.class)
public abstract class NetworkManagerMixin {
    // Runs before every return of channelActive.
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
