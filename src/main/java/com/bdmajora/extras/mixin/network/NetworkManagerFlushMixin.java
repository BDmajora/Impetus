package com.bdmajora.extras.mixin.network;

import com.bdmajora.extras.network.FlushBatch;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import net.minecraft.network.EnumConnectionState;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraftforge.fml.common.network.internal.FMLProxyPacket;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import javax.annotation.Nullable;

// Mixin that changes NetworkManager.
@Mixin(NetworkManager.class)
public abstract class NetworkManagerFlushMixin implements FlushBatch.Deferrable {
    // Reference to the field channel of the target.
    @Shadow
    private Channel channel;

    // Reference to the field protocol attribute key of the target.
    @Shadow
    @Final
    public static AttributeKey<EnumConnectionState> PROTOCOL_ATTRIBUTE_KEY;

    // Reference to the field logger of the target.
    @Shadow
    @Final
    private static org.apache.logging.log4j.Logger LOGGER;

    // Reference to the method set connection state of the target.
    @Shadow
    public abstract void setConnectionState(EnumConnectionState state);

    // Flag set while a flush is scheduled.
    private volatile boolean impetus$flushPending;

    // Replaces the whole method dispatchPacket.
    @Overwrite
    private void dispatchPacket(final Packet<?> packet, @Nullable final GenericFutureListener<? extends Future<? super Void>>[] listeners) {
        final EnumConnectionState packetState = EnumConnectionState.getFromPacket(packet);
        final EnumConnectionState channelState = this.channel.attr(PROTOCOL_ATTRIBUTE_KEY).get();
        final boolean switchState = packetState != channelState && !(packet instanceof FMLProxyPacket);
        if (switchState) {
            LOGGER.debug("Disabled auto read");
            this.channel.config().setAutoRead(false);
        }
        final boolean defer = listeners == null && FlushBatch.shouldDefer();
        if (this.channel.eventLoop().inEventLoop()) {
            this.impetus$write(packet, listeners, packetState, switchState, defer);
        } else {
            this.channel.eventLoop().execute(() -> this.impetus$write(packet, listeners, packetState, switchState, defer));
        }
    }

    // Handles write.
    private void impetus$write(Packet<?> packet, GenericFutureListener<? extends Future<? super Void>>[] listeners,
                               // Connection state and switch and defer flags.
                               EnumConnectionState packetState, boolean switchState, boolean defer) {
        if (switchState) {
            this.setConnectionState(packetState);
        }
        ChannelFuture future;
        if (defer) {
            this.impetus$flushPending = true;
            future = this.channel.write(packet);
        } else {
            future = this.channel.writeAndFlush(packet);
        }
        if (listeners != null) {
            future.addListeners(listeners);
        }
        future.addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
    }

    // Handles flush deferred.
    @Override
    public void impetus$flushDeferred() {
        if (this.impetus$flushPending) {
            this.impetus$flushPending = false;
            this.channel.flush();
        }
    }
}
