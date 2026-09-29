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
        GlBufferArena arena = new GlBufferArena(commands, 16, 4, new FallbackStagingBuffer(commands));
        assertEquals(64, arena.getDeviceAllocatedMemoryL());
        assertTrue(arena.isEmpty());
        PendingUpload u0 = upload(16), u1 = upload(16), u2 = upload(16);
        // The queue is consumed by the call, so the uploads are held separately
        assertFalse(arena.upload(commands, new ArrayList<>(List.of(u0, u1, u2))));
        assertEquals(48, arena.getDeviceUsedMemoryL());
        assertFalse(arena.isEmpty());
        GlBuffer buffer = arena.getBufferObject();
        GlBufferSegment s0 = u0.getResult();
        GlBufferSegment s1 = u1.getResult();
        GlBufferSegment s2 = u2.getResult();
        assertEquals(4, s0.getLength());
        assertEquals(12, s0.getOffset());
        assertEquals(8, s1.getOffset());
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

        // The arena is full after 16 elements; the next upload grows and compacts it
        PendingUpload exact = upload(64);
        arena.upload(commands, new ArrayList<>(List.of(exact)));
        assertEquals(0, exact.getResult().getOffset());
        PendingUpload m0 = upload(8), m1 = upload(8);
        assertTrue(arena.upload(commands, new ArrayList<>(List.of(m0, m1))));
        assertNotSame(buffer, arena.getBufferObject());
        assertEquals(24, arena.getDeviceAllocatedMemoryL() / 4);
        exact.getResult().delete();
        m0.getResult().delete();
        assertTrue(arena.upload(commands, new ArrayList<>(List.of(upload(8), upload(16), upload(100)))));
        m1.getResult().delete();
        arena.ensureCapacity(commands, 1000);
        assertTrue(arena.getDeviceAllocatedMemoryL() / 4 >= 1000);
        assertFalse(arena.isDeleted());
        arena.delete(commands);
        assertTrue(arena.isDeleted());
    }

    @Test
    void invariantCheckerAcceptsAHealthyArena() throws ReflectiveOperationException {
        GlBufferArena arena = new GlBufferArena(commands, 8, 4, new FallbackStagingBuffer(commands));
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
        var thrown = assertThrows(java.lang.reflect.InvocationTargetException.class, () -> resize.invoke(arena, commands, 1));
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
