package com.bdmajora.impetus.engine.impl.gl.arena.staging;

import com.bdmajora.impetus.engine.impl.gl.buffer.GlBufferUsage;
import com.bdmajora.impetus.engine.impl.gl.buffer.GlMutableBuffer;
import com.bdmajora.impetus.engine.impl.gl.device.CommandList;
import com.bdmajora.impetus.engine.impl.gl.device.GLRenderDevice;
import com.bdmajora.impetus.engine.impl.gl.device.RenderDevice;
import com.bdmajora.impetus.engine.impl.gl.functions.BufferCopyFunctions;
import com.bdmajora.impetus.engine.impl.gl.functions.BufferMapRangeFunctions;
import com.bdmajora.impetus.engine.impl.gl.functions.BufferStorageFunctions;
import com.bdmajora.impetus.engine.impl.gl.functions.DeviceFunctions;
import com.bdmajora.impetus.engine.impl.gl.functions.MultidrawFunctions;
import com.bdmajora.testing.TestGl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import org.lwjgl.system.MemoryUtil;

import static org.junit.jupiter.api.Assertions.*;

class StagingBuffersTest {
    private GLRenderDevice device;
    private CommandList commands;

    @BeforeEach
    void activate() {
        device = com.bdmajora.testing.Devices.active();
        commands = Mockito.spy(device.createCommandList());
    }

    @AfterEach
    void deactivate() {
        device.makeInactive();
    }

    @Test
    void fallbackUploadsThroughAScratchBuffer() {
        FallbackStagingBuffer staging = new FallbackStagingBuffer(commands);
        GlMutableBuffer dst = commands.createMutableBuffer();
        ByteBuffer data = ByteBuffer.allocateDirect(8);
        staging.enqueueCopy(commands, data, dst, 16);
        Mockito.verify(commands).uploadData(Mockito.any(GlMutableBuffer.class), Mockito.eq(data), Mockito.eq(GlBufferUsage.STREAM_COPY));
        Mockito.verify(commands).copyBufferSubData(Mockito.any(), Mockito.eq(dst), Mockito.eq(0L), Mockito.eq(16L), Mockito.eq(8L));
        staging.flush(commands);
        Mockito.verify(commands).allocateStorage(Mockito.any(), Mockito.eq(0L), Mockito.eq(GlBufferUsage.STREAM_COPY));
        staging.flip();
        assertEquals("Fallback", staging.toString());
        staging.delete(commands);
    }

    @Test
    void mappedRingSplitsWrapsFencesAndFallsBack() {
        MappedStagingBuffer staging = new MappedStagingBuffer(commands, 32);
        assertEquals("Mapped (0/0 MiB)", staging.toString());
        GlMutableBuffer dst = commands.createMutableBuffer();
        GlMutableBuffer other = commands.createMutableBuffer();
        ByteBuffer twelve = MemoryUtil.memCalloc(12);
        staging.enqueueCopy(commands, twelve, dst, 0);
        staging.enqueueCopy(commands, twelve, dst, 12);
        staging.flush(commands);
        Mockito.verify(commands).copyBufferSubData(Mockito.any(), Mockito.eq(dst), Mockito.eq(0L), Mockito.eq(0L), Mockito.eq(24L));
        Mockito.verify(commands).flushMappedRange(Mockito.any(), Mockito.eq(0), Mockito.eq(24));
        staging.flush(commands);

        // Only 8 bytes of the ring are unfenced, so a larger copy takes the fallback path
        ByteBuffer big = MemoryUtil.memCalloc(64);
        staging.enqueueCopy(commands, big, dst, 0);
        Mockito.verify(commands).uploadData(Mockito.any(GlMutableBuffer.class), Mockito.eq(big), Mockito.eq(GlBufferUsage.STREAM_COPY));

        // Once the fence signals the region is reclaimed; the next copy then wraps around the end of the ring
        Mockito.doAnswer(inv -> {
            inv.<IntBuffer>getArgument(2).put(0, 1);
            return 0x9119;
        }).when(TestGl.gl()).glGetSynci(Mockito.anyLong(), Mockito.anyInt(), Mockito.any());
        staging.flip();
        staging.enqueueCopy(commands, twelve, other, 0);
        staging.flush(commands);
        Mockito.verify(commands).copyBufferSubData(Mockito.any(), Mockito.eq(other), Mockito.eq(24L), Mockito.eq(0L), Mockito.eq(8L));
        Mockito.verify(commands).copyBufferSubData(Mockito.any(), Mockito.eq(other), Mockito.eq(0L), Mockito.eq(8L), Mockito.eq(4L));
        Mockito.verify(commands).flushMappedRange(Mockito.any(), Mockito.eq(24), Mockito.eq(8));
        Mockito.verify(commands).flushMappedRange(Mockito.any(), Mockito.eq(0), Mockito.eq(4));
        Mockito.doAnswer(inv -> {
            inv.<IntBuffer>getArgument(2).put(0, 1);
            return 0x9118;
        }).when(TestGl.gl()).glGetSynci(Mockito.anyLong(), Mockito.anyInt(), Mockito.any());
        staging.flip();
        staging.delete(commands);
        MemoryUtil.memFree(twelve);
        MemoryUtil.memFree(big);
    }

    @Test
    void supportRequiresStorageCopyAndMapRange() {
        RenderDevice device = Mockito.mock(RenderDevice.class);
        Mockito.when(device.getDeviceFunctions()).thenReturn(new DeviceFunctions(BufferStorageFunctions.CORE, MultidrawFunctions.CORE, BufferCopyFunctions.CORE, BufferMapRangeFunctions.CORE));
        assertTrue(MappedStagingBuffer.isSupported(device));
        Mockito.when(device.getDeviceFunctions()).thenReturn(new DeviceFunctions(BufferStorageFunctions.NONE, MultidrawFunctions.CORE, BufferCopyFunctions.CORE, BufferMapRangeFunctions.CORE));
        assertFalse(MappedStagingBuffer.isSupported(device));
        Mockito.when(device.getDeviceFunctions()).thenReturn(new DeviceFunctions(BufferStorageFunctions.CORE, MultidrawFunctions.CORE, BufferCopyFunctions.PIXEL_PACK, BufferMapRangeFunctions.CORE));
        assertFalse(MappedStagingBuffer.isSupported(device));
        Mockito.when(device.getDeviceFunctions()).thenReturn(new DeviceFunctions(BufferStorageFunctions.CORE, MultidrawFunctions.CORE, BufferCopyFunctions.CORE, BufferMapRangeFunctions.MAP_FULL_AND_SLICE));
        assertFalse(MappedStagingBuffer.isSupported(device));
        new MappedStagingBuffer(commands).delete(commands);
    }
}
