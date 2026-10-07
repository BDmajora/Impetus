package com.bdmajora.extras.mixin.network;

import com.bdmajora.extras.Extras;
import com.bdmajora.extras.ExtrasConfig;
import com.bdmajora.extras.network.CompressionScratch;
import com.bdmajora.extras.network.FlushBatch;
import com.bdmajora.extras.network.NetworkLimits;
import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import com.google.common.util.concurrent.ListenableFuture;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoop;
import io.netty.handler.codec.CorruptedFrameException;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.util.Attribute;
import io.netty.util.concurrent.GenericFutureListener;
import net.minecraft.network.EnumConnectionState;
import net.minecraft.network.INetHandler;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.NetworkSystem;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.server.SPacketKeepAlive;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.IThreadListener;
import net.minecraftforge.fml.common.network.internal.FMLProxyPacket;
import org.apache.logging.log4j.LogManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.FutureTask;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NetworkMixinsTest {
    private ExtrasConfig config;

    @BeforeAll
    static void bootstrap() {
        Mc.bootstrap();
    }

    @BeforeEach
    void installConfig() {
        config = new ExtrasConfig();
        Mixins.set(Extras.class, "config", config);
        // Without LittleTiles, whose absence is otherwise looked up in a mod table these tests never install
        Mixins.set(NetworkLimits.class, "littleTiles", false);
    }

    @AfterEach
    void removeConfig() {
        FlushBatch.endTick();
        Mixins.set(Extras.class, "config", null);
        Mixins.set(NetworkLimits.class, "littleTiles", null);
    }

    @Test
    void everyVanillaLimitIsReplacedByTheSwitch() {
        config.network.largePackets = false;
        assertEquals(NetworkLimits.VANILLA_SERVERBOUND_PAYLOAD,
                (int) Mixins.call(Mixins.instance(CPacketCustomPayloadMixin.class), "impetus$payloadLimit", 32767));
        assertEquals(NetworkLimits.VANILLA_CLIENTBOUND_PAYLOAD,
                (int) Mixins.call(Mixins.instance(SPacketCustomPayloadMixin.class), "impetus$payloadLimit", 1048576));
        assertEquals(NetworkLimits.VANILLA_CHUNK_DATA,
                (int) Mixins.call(Mixins.instance(SPacketChunkDataMixin.class), "impetus$chunkDataLimit", 2097152));
        assertEquals(3, (int) Mixins.call(Mixins.instance(NettyVarint21FrameEncoderMixin.class), "impetus$frameLengthBytes", 3));
        assertEquals(3, (int) Mixins.call(Mixins.instance(NettyVarint21FrameDecoderMixin.class), "impetus$frameLengthBytes", 3));

        config.network.largePackets = true;
        config.network.loginTimeoutSeconds = 30;
        config.network.keepAliveTimeoutSeconds = 45;
        assertEquals(600, (int) Mixins.call(Mixins.instance(NetHandlerLoginServerMixin.class), "impetus$loginTimeout", 600));
        assertEquals(45_000L, (long) Mixins.call(Mixins.instance(NetHandlerPlayServerMixin.class), "impetus$keepAliveTimeout", 15000L));

        PacketBufferMixin buffer = Mixins.instance(PacketBufferMixin.class);
        assertEquals(Long.MAX_VALUE, (long) Mixins.call(buffer, "impetus$nbtLimit", 2097152L));
        assertEquals(Integer.MAX_VALUE / 8, (int) Mixins.call(buffer, "impetus$writeStringLimit", 32767));
        // Only the generic cap is widened; a small explicit one stays as the caller asked
        assertEquals(Integer.MAX_VALUE / 8, (int) Mixins.call(buffer, "impetus$readStringLimit", NetworkLimits.VANILLA_STRING));
        assertEquals(16, (int) Mixins.call(buffer, "impetus$readStringLimit", 16));
    }

    @Test
    void varIntsGoThroughTheTableWhenSwitchedOn() {
        CallbackInfoReturnable<Integer> size = Mixins.cir();
        Mixins.call(PacketBufferVarIntMixin.class, "impetus$tableSize", 300, size);
        assertEquals(2, size.getReturnValue());

        PacketBufferVarIntMixin buffer = Mixins.instance(PacketBufferVarIntMixin.class);
        ByteBuf backing = Unpooled.buffer();
        Mixins.set(buffer, "buf", backing);
        CallbackInfoReturnable<PacketBuffer> written = Mixins.cir();
        Mixins.call(buffer, "impetus$wideWrite", 300, written);
        assertSame(buffer, written.getReturnValue());
        assertEquals(2, backing.readableBytes());

        // Switched off, vanilla's own methods answer
        config.network.fastVarInts = false;
        CallbackInfoReturnable<Integer> vanillaSize = Mixins.cir();
        Mixins.call(PacketBufferVarIntMixin.class, "impetus$tableSize", 300, vanillaSize);
        assertFalse(vanillaSize.isCancelled());
        CallbackInfoReturnable<PacketBuffer> vanillaWrite = Mixins.cir();
        Mixins.call(buffer, "impetus$wideWrite", 300, vanillaWrite);
        assertFalse(vanillaWrite.isCancelled());
    }

    private static ChannelHandlerContext context() {
        ChannelHandlerContext ctx = mock(ChannelHandlerContext.class);
        when(ctx.alloc()).thenReturn(UnpooledByteBufAllocator.DEFAULT);
        return ctx;
    }

    private static NettyCompressionEncoderMixin encoder() {
        NettyCompressionEncoderMixin encoder = Mixins.instance(NettyCompressionEncoderMixin.class);
        Mixins.set(encoder, "buffer", new byte[8192]);
        Mixins.set(encoder, "deflater", new Deflater());
        Mixins.set(encoder, "threshold", 256);
        Mixins.set(encoder, "impetus$scratch", new CompressionScratch());
        return encoder;
    }

    private static NettyCompressionDecoderMixin decoder() {
        NettyCompressionDecoderMixin decoder = Mixins.instance(NettyCompressionDecoderMixin.class);
        Mixins.set(decoder, "inflater", new Inflater());
        Mixins.set(decoder, "threshold", 256);
        Mixins.set(decoder, "impetus$scratch", new CompressionScratch());
        return decoder;
    }

    private static byte[] roundTrip(byte[] payload, boolean directInput) {
        ByteBuf in = directInput ? Unpooled.directBuffer(payload.length).writeBytes(payload) : Unpooled.wrappedBuffer(payload);
        ByteBuf wire = Unpooled.buffer();
        Mixins.call(encoder(), "encode", context(), in, wire);
        List<Object> out = new ArrayList<>();
        Mixins.call(decoder(), "decode", context(), wire, out);
        ByteBuf decoded = (ByteBuf) out.get(0);
        byte[] bytes = new byte[decoded.readableBytes()];
        decoded.readBytes(bytes);
        return bytes;
    }

    @Test
    void packetsSurviveCompressionEitherWay() {
        byte[] payload = new byte[5000];
        new Random(1).nextBytes(payload);
        byte[] small = {1, 2, 3};
        for (boolean pooled : new boolean[] {true, false}) {
            config.network.pooledCompression = pooled;
            assertArrayEquals(payload, roundTrip(payload, false));
            assertArrayEquals(payload, roundTrip(payload, true));
            // Below the threshold a packet goes out uncompressed behind a zero length
            assertArrayEquals(small, roundTrip(small, false));
        }

        // Nothing readable is nothing to decode
        List<Object> none = new ArrayList<>();
        Mixins.call(decoder(), "decode", context(), Unpooled.buffer(), none);
        assertTrue(none.isEmpty());
    }

    @Test
    void aBadlyCompressedPacketIsRefused() {
        // Claimed below the threshold
        PacketBuffer tooSmall = new PacketBuffer(Unpooled.buffer());
        tooSmall.writeVarInt(10);
        assertThrows(DecoderException.class, () -> Mixins.call(decoder(), "decode", context(), tooSmall, new ArrayList<>()));
        // Claimed past the protocol maximum
        config.network.largePackets = false;
        PacketBuffer tooLarge = new PacketBuffer(Unpooled.buffer());
        tooLarge.writeVarInt(NetworkLimits.VANILLA_COMPRESSED_PACKET + 1);
        assertThrows(DecoderException.class, () -> Mixins.call(decoder(), "decode", context(), tooLarge, new ArrayList<>()));
        // Garbage in place of deflate data fails the inflate and frees what was allocated
        PacketBuffer garbage = new PacketBuffer(Unpooled.buffer());
        garbage.writeVarInt(300);
        garbage.writeBytes(new byte[] {1, 2, 3, 4, 5, 6, 7, 8});
        assertThrows(Exception.class, () -> Mixins.call(decoder(), "decode", context(), garbage, new ArrayList<>()));
        // A stream that stops short of the claimed size yields what it holds
        Deflater deflater = new Deflater();
        deflater.setInput(new byte[300]);
        deflater.finish();
        byte[] compressed = new byte[600];
        int n = deflater.deflate(compressed);
        PacketBuffer truncated = new PacketBuffer(Unpooled.buffer());
        truncated.writeVarInt(300);
        truncated.writeBytes(compressed, 0, n / 2);
        List<Object> partial = new ArrayList<>();
        Mixins.call(decoder(), "decode", context(), truncated, partial);
        assertTrue(((ByteBuf) partial.get(0)).readableBytes() < 300);
    }

    private static ChannelHandlerContext activeContext(boolean active) {
        ChannelHandlerContext ctx = context();
        Channel channel = mock(Channel.class);
        when(channel.isActive()).thenReturn(active);
        when(ctx.channel()).thenReturn(channel);
        return ctx;
    }

    @Test
    void framesAreSplitWithoutReadingThePrefixTwice() {
        NettyVarint21FrameDecoderMixin decoder = Mixins.instance(NettyVarint21FrameDecoderMixin.class);
        // Switched off, vanilla decodes
        config.network.fastVarInts = false;
        CallbackInfo off = Mixins.ci();
        Mixins.call(decoder, "impetus$fastDecode", activeContext(true), Unpooled.buffer(), new ArrayList<>(), off);
        assertFalse(off.isCancelled());
        config.network.fastVarInts = true;

        // A closed channel or a run of padding is dropped
        ByteBuf closed = Unpooled.wrappedBuffer(new byte[] {1, 5});
        Mixins.call(decoder, "impetus$fastDecode", activeContext(false), closed, new ArrayList<>(), Mixins.ci());
        assertEquals(0, closed.readableBytes());
        ByteBuf padding = Unpooled.wrappedBuffer(new byte[] {0, 0, 0});
        Mixins.call(decoder, "impetus$fastDecode", activeContext(true), padding, new ArrayList<>(), Mixins.ci());
        assertEquals(0, padding.readableBytes());

        // A whole frame after leading padding comes out as a slice
        List<Object> out = new ArrayList<>();
        ByteBuf frame = Unpooled.wrappedBuffer(new byte[] {0, 3, 7, 8, 9});
        CallbackInfo ci = Mixins.ci();
        Mixins.call(decoder, "impetus$fastDecode", activeContext(true), frame, out, ci);
        assertTrue(ci.isCancelled());
        assertEquals(3, ((ByteBuf) out.get(0)).readableBytes());

        // A frame whose body or prefix has not fully arrived waits, consuming nothing
        ByteBuf shortBody = Unpooled.wrappedBuffer(new byte[] {5, 1});
        Mixins.call(decoder, "impetus$fastDecode", activeContext(true), shortBody, new ArrayList<>(), Mixins.ci());
        assertEquals(2, shortBody.readableBytes());
        ByteBuf shortPrefix = Unpooled.wrappedBuffer(new byte[] {(byte) 0x80});
        Mixins.call(decoder, "impetus$fastDecode", activeContext(true), shortPrefix, new ArrayList<>(), Mixins.ci());
        assertEquals(1, shortPrefix.readableBytes());

        // A negative length, and one wider than the limit, are both refused
        ByteBuf negative = Unpooled.wrappedBuffer(new byte[] {(byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, 0x0F});
        assertThrows(DecoderException.class,
                () -> Mixins.call(decoder, "impetus$fastDecode", activeContext(true), negative, new ArrayList<>(), Mixins.ci()));
        ByteBuf wide = Unpooled.wrappedBuffer(new byte[] {(byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, 1});
        assertThrows(CorruptedFrameException.class,
                () -> Mixins.call(decoder, "impetus$fastDecode", activeContext(true), wide, new ArrayList<>(), Mixins.ci()));
    }

    @SuppressWarnings("unchecked")
    private static Channel channel(EnumConnectionState state, boolean inEventLoop) {
        Channel channel = mock(Channel.class);
        Attribute<EnumConnectionState> attribute = mock(Attribute.class);
        when(attribute.get()).thenReturn(state);
        when(channel.attr(any())).thenReturn((Attribute) attribute);
        when(channel.config()).thenReturn(mock(ChannelConfig.class));
        EventLoop loop = mock(EventLoop.class);
        when(loop.inEventLoop()).thenReturn(inEventLoop);
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(0).run();
            return null;
        }).when(loop).execute(any());
        when(channel.eventLoop()).thenReturn(loop);
        ChannelFuture future = mock(ChannelFuture.class);
        when(channel.write(any())).thenReturn(future);
        when(channel.writeAndFlush(any())).thenReturn(future);
        return channel;
    }

    @Test
    void packetsWrittenDuringATickAreFlushedOnceAtItsEnd() {
        Mixins.set(NetworkManagerFlushMixin.class, "LOGGER", LogManager.getLogger("test"));
        NetworkManagerFlushMixin manager = Mixins.instance(NetworkManagerFlushMixin.class);
        SPacketKeepAlive packet = new SPacketKeepAlive(1L);

        // Outside a tick every packet is flushed as it goes, from the event loop or handed to it
        Channel direct = channel(EnumConnectionState.PLAY, true);
        Mixins.set(manager, "channel", direct);
        Mixins.call(manager, "dispatchPacket", packet, null);
        verify(direct).writeAndFlush(packet);
        Channel handedOver = channel(EnumConnectionState.PLAY, false);
        Mixins.set(manager, "channel", handedOver);
        GenericFutureListener<io.netty.util.concurrent.Future<Void>> listener = future -> { };
        GenericFutureListener<?>[] listeners = {listener};
        Mixins.call(manager, "dispatchPacket", packet, listeners);
        verify(handedOver).writeAndFlush(packet);

        // A packet from another protocol state switches the channel first, unless Forge proxies it
        Channel switching = channel(EnumConnectionState.HANDSHAKING, true);
        Mixins.set(manager, "channel", switching);
        Mixins.call(manager, "dispatchPacket", packet, null);
        verify(manager).setConnectionState(EnumConnectionState.PLAY);
        FMLProxyPacket proxy = mock(FMLProxyPacket.class);
        Mixins.call(manager, "dispatchPacket", proxy, null);

        // Inside a tick writes are held back, and the end of the tick flushes once
        FlushBatch.beginTick();
        Channel batched = channel(EnumConnectionState.PLAY, true);
        Mixins.set(manager, "channel", batched);
        Mixins.call(manager, "dispatchPacket", packet, null);
        Mixins.call(manager, "dispatchPacket", packet, null);
        verify(batched, never()).writeAndFlush(any());
        manager.impetus$flushDeferred();
        manager.impetus$flushDeferred();
        verify(batched).flush();
    }

    @Test
    void theServerFlushesEveryOpenConnectionAtTheEndOfItsTick() {
        MinecraftServerFlushMixin server = Mixins.instance(MinecraftServerFlushMixin.class);
        Mixins.call(server, "impetus$beginBatch", Mixins.ci());
        assertTrue(FlushBatch.shouldDefer());
        // No network system yet, nothing to flush
        Mixins.call(server, "impetus$flushAll", Mixins.ci());
        assertFalse(FlushBatch.shouldDefer());

        NetworkSystem system = Mc.mock(NetworkSystem.class, NetworkSystemAccessor.class);
        NetworkManager open = Mc.mock(NetworkManager.class, FlushBatch.Deferrable.class);
        when(open.isChannelOpen()).thenReturn(true);
        NetworkManager closed = Mc.mock(NetworkManager.class, FlushBatch.Deferrable.class);
        NetworkManager local = Mc.mock(NetworkManager.class, FlushBatch.Deferrable.class);
        when(local.hasNoChannel()).thenReturn(true);
        when(((NetworkSystemAccessor) system).impetus$networkManagers()).thenReturn(new ArrayList<>(List.of(open, closed, local)));
        doReturn(system).when((MinecraftServer) (Object) server).getNetworkSystem();
        Mixins.call(server, "impetus$flushAll", Mixins.ci());
        verify((FlushBatch.Deferrable) open).impetus$flushDeferred();
        verify((FlushBatch.Deferrable) closed, never()).impetus$flushDeferred();
        verify((FlushBatch.Deferrable) local, never()).impetus$flushDeferred();
    }

    @Test
    void theReadTimeoutFollowsTheSetting() {
        NetworkManagerMixin manager = Mixins.instance(NetworkManagerMixin.class);
        ChannelHandlerContext ctx = activeContext(true);
        ChannelPipeline pipeline = mock(ChannelPipeline.class);
        when(ctx.channel().pipeline()).thenReturn(pipeline);
        // A local connection has no timeout handler to replace
        Mixins.call(manager, "impetus$widenReadTimeout", ctx, Mixins.ci());
        verify(pipeline, never()).replace(anyString(), anyString(), any());

        when(pipeline.get("timeout")).thenReturn(new ReadTimeoutHandler(30));
        config.network.readTimeoutSeconds = 0;
        Mixins.call(manager, "impetus$widenReadTimeout", ctx, Mixins.ci());
        verify(pipeline, never()).replace(anyString(), anyString(), any());
        config.network.readTimeoutSeconds = 120;
        Mixins.call(manager, "impetus$widenReadTimeout", ctx, Mixins.ci());
        verify(pipeline).replace(eq("timeout"), eq("timeout"), any(ReadTimeoutHandler.class));
    }

    @Test
    void theClientRunsItsQueuedTasksOutsideTheLock() {
        MinecraftScheduledTasksMixin client = Mixins.instance(MinecraftScheduledTasksMixin.class);
        Mixins.set(MinecraftScheduledTasksMixin.class, "LOGGER", LogManager.getLogger("test"));
        Queue<FutureTask<?>> queue = new ArrayDeque<>();
        List<String> ran = new ArrayList<>();
        queue.add(new FutureTask<>(() -> ran.add("packet"), null));
        Mixins.set(client, "scheduledTasks", queue);
        Mixins.call(client, "impetus$drainOutsideLock", Mixins.ci());
        assertEquals(List.of("packet"), ran);
        assertTrue(queue.isEmpty());
        // Vanilla's own synchronized loop is always told there is nothing left to run
        assertTrue((boolean) Mixins.call(client, "impetus$skipLockedDrain", false));
    }

    @Test
    void packetsQueuedForADisconnectedClientAreDropped() {
        assertNotNull(Mixins.instance(PacketThreadUtilMixin.class));
        NetHandlerPlayClientRetireMixin connection = Mixins.instance(NetHandlerPlayClientRetireMixin.class);
        assertFalse(connection.impetus$isRetired());
        IThreadListener scheduler = mock(IThreadListener.class);
        Mc.Recorded<ListenableFuture<Object>> enqueue = Mc.operation(null);
        List<String> ran = new ArrayList<>();
        Runnable packet = () -> ran.add("packet");

        // Server handlers are queued untouched
        INetHandler server = mock(INetHandler.class);
        Mixins.call(PacketThreadUtilMixin.class, "impetus$skipOnceRetired", scheduler, packet, enqueue, server);
        assertSame(scheduler, enqueue.last()[0]);
        assertSame(packet, enqueue.last()[1]);

        // A client packet runs while the connection is up and is skipped once loadWorld(null) has cleaned the handler up
        Mixins.call(PacketThreadUtilMixin.class, "impetus$skipOnceRetired", scheduler, packet, enqueue, connection);
        Runnable live = (Runnable) enqueue.last()[1];
        Mixins.call(PacketThreadUtilMixin.class, "impetus$skipOnceRetired", scheduler, packet, enqueue, connection);
        Runnable stale = (Runnable) enqueue.last()[1];
        assertNotSame(packet, stale);
        live.run();
        Mixins.call(connection, "impetus$retire", Mixins.ci());
        assertTrue(connection.impetus$isRetired());
        stale.run();
        assertEquals(List.of("packet"), ran);
    }
}
