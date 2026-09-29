package com.bdmajora.extras.network;

import com.bdmajora.extras.Extras;
import com.bdmajora.extras.ExtrasConfig;
import com.bdmajora.testing.Mixins;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketBuffer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class NetworkHelpersTest {
    private ExtrasConfig config;

    @BeforeEach
    void installConfig() {
        config = new ExtrasConfig();
        Mixins.set(Extras.class, "config", config);
    }

    @AfterEach
    void removeConfig() {
        FlushBatch.endTick();
        Mixins.set(Extras.class, "config", null);
    }

    @Test
    void theTableVarIntMatchesVanillaByteForByte() {
        for (int value : new int[] {0, 1, 127, 128, 16383, 16384, 2097151, 2097152, 268435455, 268435456, Integer.MAX_VALUE, -1}) {
            ByteBuf ours = Unpooled.buffer();
            VarInts.write(ours, value);
            PacketBuffer vanilla = new PacketBuffer(Unpooled.buffer());
            vanilla.writeVarInt(value);
            assertEquals(ByteBufUtil.hexDump(vanilla), ByteBufUtil.hexDump(ours), Integer.toString(value));
            assertEquals(PacketBuffer.getVarIntSize(value), VarInts.size(value), Integer.toString(value));
        }
        assertNotNull(Mixins.construct(VarInts.class));
    }

    @Test
    void theScratchReadsHeapBuffersInPlaceAndCopiesDirectOnes() {
        CompressionScratch scratch = new CompressionScratch();
        ByteBuf heap = Unpooled.wrappedBuffer(new byte[] {9, 1, 2, 3});
        heap.readByte();
        scratch.load(heap);
        assertSame(heap.array(), scratch.array);
        assertEquals(1, scratch.offset);
        assertEquals(3, scratch.length);
        assertEquals(0, heap.readableBytes());

        // A direct buffer is copied, growing the scratch when it is too small
        byte[] big = new byte[20_000];
        Arrays.fill(big, (byte) 7);
        ByteBuf direct = Unpooled.directBuffer(big.length).writeBytes(big);
        scratch.load(direct);
        assertEquals(0, scratch.offset);
        assertEquals(big.length, scratch.length);
        assertEquals(7, scratch.array[19_999]);
        scratch.release();
        assertNull(scratch.array);

        // A huge packet's scratch is not kept afterwards
        ByteBuf huge = Unpooled.directBuffer(2 << 20).writeZero(2 << 20);
        scratch.load(huge);
        scratch.release();
        assertTrue(Mixins.<byte[]>get(scratch, "scratch").length < (1 << 20));
        direct.release();
        huge.release();
    }

    @Test
    void aBadLengthCarriesNoStackTrace() {
        QuietDecoderException quiet = new QuietDecoderException("Bad packet length");
        assertEquals(0, quiet.getStackTrace().length);
        assertEquals("Bad packet length", quiet.getMessage());
    }

    @Test
    void flushesAreOnlyDeferredInsideAServerTick() throws InterruptedException {
        // A thread that never began a tick is outside one
        boolean[] fresh = {true};
        Thread other = new Thread(() -> fresh[0] = FlushBatch.shouldDefer());
        other.start();
        other.join();
        assertFalse(fresh[0]);
        assertFalse(FlushBatch.shouldDefer());
        FlushBatch.beginTick();
        assertTrue(FlushBatch.shouldDefer());
        config.network.flushConsolidation = false;
        assertFalse(FlushBatch.shouldDefer());
        config.network.flushConsolidation = true;
        FlushBatch.endTick();
        assertFalse(FlushBatch.shouldDefer());
        assertNotNull(Mixins.construct(FlushBatch.class));
    }
}
