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

// Forge's dispatchPacket with the flush made conditional: a write issued from inside the server tick is queued and flushed once at the end of the tick (MinecraftServerFlushMixin), unless it carries listeners, since a disconnect packet's listener closes the channel on completion and must not wait. Overwritten rather than injected because the second call site lives in an anonymous Runnable. Forge's own patch to this method must be carried too: an FMLProxyPacket (the FML|HS handshake, REGISTER) is in no EnumConnectionState, so getFromPacket returns null for it and vanilla's state switch would set the channel protocol to null, which NettyPacketEncoder rejects as "ConnectionProtocol unknown" and kills every remote join at the first handshake write
@Mixin(NetworkManager.class)
public abstract class NetworkManagerFlushMixin implements FlushBatch.Deferrable {
    @Shadow
    private Channel channel;

    @Shadow
    @Final
    public static AttributeKey<EnumConnectionState> PROTOCOL_ATTRIBUTE_KEY;

    @Shadow
    @Final
    private static org.apache.logging.log4j.Logger LOGGER;

    @Shadow
    public abstract void setConnectionState(EnumConnectionState state);

    // Written on the calling thread and the event loop, read at end of tick on the server thread
    private volatile boolean impetus$flushPending;

    @Overwrite
    private void dispatchPacket(final Packet<?> packet, @Nullable final GenericFutureListener<? extends Future<? super Void>>[] listeners) {
        final EnumConnectionState packetState = EnumConnectionState.getFromPacket(packet);
        final EnumConnectionState channelState = this.channel.attr(PROTOCOL_ATTRIBUTE_KEY).get();
        // Forge: a proxy packet rides on whatever state the channel is in and never switches it
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

    private void impetus$write(Packet<?> packet, GenericFutureListener<? extends Future<? super Void>>[] listeners,
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

    @Override
    public void impetus$flushDeferred() {
        if (this.impetus$flushPending) {
            this.impetus$flushPending = false;
            this.channel.flush();
        }
    }
}
