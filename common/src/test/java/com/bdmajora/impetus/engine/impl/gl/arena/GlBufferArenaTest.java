package com.bdmajora.impetus.engine.impl.gl.arena;

import com.bdmajora.impetus.engine.impl.common.util.NativeBuffer;
import com.bdmajora.impetus.engine.impl.gl.arena.staging.FallbackStagingBuffer;
import com.bdmajora.impetus.engine.impl.gl.buffer.GlBuffer;
import com.bdmajora.impetus.engine.impl.gl.device.CommandList;
import com.bdmajora.impetus.engine.impl.gl.device.GLRenderDevice;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GlBufferArenaTest {
    private GLRenderDevice device;
    private CommandList commands;

    @BeforeEach
    void activate() {
        device = com.bdmajora.testing.Devices.active();
        commands = device.createCommandList();
    }

    @AfterEach
    void deactivate() {
        device.makeInactive();
    }

    private static PendingUpload upload(int bytes) {
        return PendingUpload.of(new NativeBuffer(bytes));
    }

    @Test
    void allocatesFreesAndCoalesces() {
        // Four-byte elements; nothing is allocated until the first upload, which grows to at least 16 elements
        GlBufferArena arena = new GlBufferArena(commands, 4, 64, new FallbackStagingBuffer(commands));
        assertEquals(0, arena.getDeviceAllocatedMemoryL());
        assertTrue(arena.isEmpty());
        GlBuffer empty = arena.getBufferObject();
        PendingUpload u0 = upload(16), u1 = upload(16), u2 = upload(16);
        // The queue is consumed by the call, so the uploads are held separately; growing swaps the buffer
        assertTrue(arena.upload(commands, new ArrayList<>(List.of(u0, u1, u2))));
        assertNotSame(empty, arena.getBufferObject());
        // Twelve elements needed, doubled while the arena is small
        assertEquals(24 * 4, arena.getDeviceAllocatedMemoryL());
        assertEquals(48, arena.getDeviceUsedMemoryL());
        assertFalse(arena.isEmpty());
        GlBuffer buffer = arena.getBufferObject();
        GlBufferSegment s0 = u0.getResult();
        GlBufferSegment s1 = u1.getResult();
        GlBufferSegment s2 = u2.getResult();
        // Allocations are carved from the end of the free space
        assertEquals(4, s0.getLength());
        assertEquals(20, s0.getOffset());
        assertEquals(16, s1.getOffset());
        assertEquals(12, s2.getOffset());
        s1.delete();
        assertThrows(IllegalStateException.class, s1::delete);
        assertEquals(32, arena.getDeviceUsedMemoryL());
        s0.delete();
        s2.delete();
        assertTrue(arena.isEmpty());
        assertThrows(IllegalStateException.class, () -> u0.setResult(s0));
        assertEquals(16, u0.getLength());
        assertThrows(IllegalStateException.class, () -> PendingUpload.of(new NativeBuffer(4)).getResult());
        assertNull(PendingUpload.of(null));

        // The freed space coalesced back into one segment, which an exact fit fills without growing
        PendingUpload exact = upload(96);
        assertFalse(arena.upload(commands, new ArrayList<>(List.of(exact))));
        assertSame(buffer, arena.getBufferObject());
        assertEquals(0, exact.getResult().getOffset());
        // Full: the next upload grows and compacts, moving the live data after the new free space
        PendingUpload m0 = upload(8), m1 = upload(8);
        assertTrue(arena.upload(commands, new ArrayList<>(List.of(m0, m1))));
        assertEquals(56, arena.getDeviceAllocatedMemoryL() / 4);
        assertEquals(32, exact.getResult().getOffset());
        exact.getResult().delete();
        m0.getResult().delete();
        // A partial element still takes a whole one, and best fit uses the fragments without growing
        List<PendingUpload> fragments = List.of(upload(8), upload(16), upload(6), upload(100));
        assertFalse(arena.upload(commands, new ArrayList<>(fragments)));
        assertEquals(2, fragments.get(2).getResult().getLength());
        fragments.forEach(fragment -> fragment.getResult().delete());
        m1.getResult().delete();
        // Asking for little against a mostly empty arena only compacts it
        long before = arena.getDeviceAllocatedMemoryL();
        arena.ensureCapacity(commands, 1);
        assertEquals(before, arena.getDeviceAllocatedMemoryL());
        arena.ensureCapacity(commands, 1000);
        assertEquals(2000, arena.getDeviceAllocatedMemoryL() / 4);
        assertFalse(arena.isDeleted());
        arena.delete(commands);
        assertTrue(arena.isDeleted());

        // Past 4 MiB the arena grows by half instead of doubling
        GlBufferArena large = new GlBufferArena(commands, 1024, 4, new FallbackStagingBuffer(commands));
        large.ensureCapacity(commands, 8192);
        assertEquals(12288L * 1024, large.getDeviceAllocatedMemoryL());
        large.delete(commands);
    }

    @Test
    void invariantCheckerAcceptsAHealthyArena() throws ReflectiveOperationException {
        GlBufferArena arena = new GlBufferArena(commands, 4, 8, new FallbackStagingBuffer(commands));
        PendingUpload first = upload(8);
        arena.upload(commands, new ArrayList<>(List.of(first, upload(8))));
        first.getResult().delete();
        Method check = GlBufferArena.class.getDeclaredMethod("checkAssertions0");
        check.setAccessible(true);
        check.invoke(arena);
        assertThrows(IllegalArgumentException.class, () -> new GlBufferArena(commands, 0, 4, new FallbackStagingBuffer(commands)));
        GlBufferSegment segment = new GlBufferSegment(arena, 0, 4);
        assertThrows(IllegalArgumentException.class, () -> segment.setLength(0));
        assertThrows(IllegalArgumentException.class, () -> segment.setOffset(-1));
        assertEquals(4, segment.getEnd());
        Method resize = GlBufferArena.class.getDeclaredMethod("resize", CommandList.class, int.class);
        resize.setAccessible(true);
        var thrown = assertThrows(java.lang.reflect.InvocationTargetException.class, () -> resize.invoke(arena, commands, 0));
        assertInstanceOf(UnsupportedOperationException.class, thrown.getCause());
    }

    @Test
    void uploadThatStillFailsAfterGrowingIsAnError() {
        GlBufferArena arena = Mockito.spy(new GlBufferArena(commands, 4, 4, new FallbackStagingBuffer(commands)));
        Mockito.doNothing().when(arena).ensureCapacity(Mockito.any(), Mockito.anyInt());
        List<PendingUpload> queue = new ArrayList<>(List.of(upload(64)));
        assertThrows(RuntimeException.class, () -> arena.upload(commands, queue));
    }
}
