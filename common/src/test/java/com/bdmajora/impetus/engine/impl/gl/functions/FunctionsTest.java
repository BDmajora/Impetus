package com.bdmajora.impetus.engine.impl.gl.functions;

import com.bdmajora.impetus.engine.impl.gl.buffer.GlBufferMapFlags;
import com.bdmajora.impetus.engine.impl.gl.buffer.GlBufferMapping;
import com.bdmajora.impetus.engine.impl.gl.buffer.GlBufferTarget;
import com.bdmajora.impetus.engine.impl.gl.buffer.GlMutableBuffer;
import com.bdmajora.impetus.engine.impl.gl.device.CommandList;
import com.bdmajora.impetus.engine.impl.gl.device.RenderDevice;
import com.bdmajora.impetus.engine.impl.gl.util.EnumBitField;
import com.bdmajora.impetus.lwjgl.GLExtension;
import com.bdmajora.testing.TestGl;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.nio.ByteBuffer;
import org.lwjgl.system.MemoryUtil;

import static org.junit.jupiter.api.Assertions.*;

class FunctionsTest {
    private final RenderDevice device = Mockito.mock(RenderDevice.class);

    private void versions(boolean supported) {
        Mockito.when(TestGl.gl().isOpenGLVersionSupported(Mockito.anyInt(), Mockito.anyInt())).thenReturn(supported);
        Mockito.when(TestGl.gl().isExtensionSupported(Mockito.any())).thenReturn(supported);
    }

    @Test
    void pickBestFallsBackWhenTheDriverLacksFeatures() {
        versions(true);
        assertEquals(BufferCopyFunctions.CORE, BufferCopyFunctions.pickBest(device));
        assertEquals(BufferMapRangeFunctions.CORE, BufferMapRangeFunctions.pickBest(device));
        DeviceFunctions functions = new DeviceFunctions(device);
        assertEquals(BufferCopyFunctions.CORE, functions.bufferCopyFunctions());
        assertEquals(BufferMapRangeFunctions.CORE, functions.bufferMapRangeFunctions());
        versions(false);
        assertEquals(BufferCopyFunctions.PIXEL_PACK, BufferCopyFunctions.pickBest(device));
        assertEquals(BufferMapRangeFunctions.MAP_FULL_AND_SLICE, BufferMapRangeFunctions.pickBest(device));
        // Either the core version or the extension alone is enough
        Mockito.when(TestGl.gl().isExtensionSupported(GLExtension.ARB_copy_buffer)).thenReturn(true);
        Mockito.when(TestGl.gl().isExtensionSupported(GLExtension.ARB_map_buffer_range)).thenReturn(true);
        assertEquals(BufferCopyFunctions.CORE, BufferCopyFunctions.pickBest(device));
        assertEquals(BufferMapRangeFunctions.CORE, BufferMapRangeFunctions.pickBest(device));
    }

    @Test
    void copyImplementations() {
        CommandList list = Mockito.mock(CommandList.class);
        GlMutableBuffer src = new GlMutableBuffer();
        GlMutableBuffer dst = new GlMutableBuffer();
        BufferCopyFunctions.CORE.copyBufferSubData(list, src, dst, 1, 2, 3);
        Mockito.verify(list).bindBuffer(GlBufferTarget.COPY_READ_BUFFER, src);
        Mockito.verify(TestGl.gl()).glCopyBufferSubData(0x8F36, 0x8F37, 1, 2, 3);

        assertThrows(IllegalStateException.class, () -> BufferCopyFunctions.PIXEL_PACK.copyBufferSubData(list, src, dst, 0, 0, 4));
        long srcMem = MemoryUtil.nmemCalloc(1, 16);
        long dstMem = MemoryUtil.nmemCalloc(1, 16);
        MemoryUtil.memPutInt(srcMem + 4, 42);
        Mockito.when(TestGl.gl().nglMapBuffer(GlBufferTarget.PIXEL_PACK_BUFFER.getTargetParameter(), 0x88B8)).thenReturn(srcMem);
        Mockito.when(TestGl.gl().nglMapBuffer(GlBufferTarget.PIXEL_UNPACK_BUFFER.getTargetParameter(), 0x88B9)).thenReturn(dstMem);
        BufferCopyFunctions.PIXEL_PACK.copyBufferSubData(list, src, dst, 4, 8, 4);
        assertEquals(42, MemoryUtil.memGetInt(dstMem + 8));
        // The copy that failed to map unbinds in its finally as well
        Mockito.verify(list, Mockito.times(2)).bindBuffer(GlBufferTarget.PIXEL_PACK_BUFFER, null);

        Mockito.when(TestGl.gl().nglMapBuffer(GlBufferTarget.PIXEL_UNPACK_BUFFER.getTargetParameter(), 0x88B9)).thenReturn(0L);
        assertThrows(IllegalStateException.class, () -> BufferCopyFunctions.PIXEL_PACK.copyBufferSubData(list, src, dst, 0, 0, 4));
        Mockito.when(TestGl.gl().nglMapBuffer(GlBufferTarget.PIXEL_PACK_BUFFER.getTargetParameter(), 0x88B8)).thenReturn(0L);
        assertThrows(IllegalStateException.class, () -> BufferCopyFunctions.PIXEL_PACK.copyBufferSubData(list, src, dst, 0, 0, 4));
        src.setActiveMapping(new GlBufferMapping(src, ByteBuffer.allocate(1)));
        assertThrows(IllegalStateException.class, () -> BufferCopyFunctions.PIXEL_PACK.copyBufferSubData(list, src, dst, 0, 0, 4));
        MemoryUtil.nmemFree(srcMem);
        MemoryUtil.nmemFree(dstMem);
    }

    @Test
    void mapRangeImplementations() {
        GlMutableBuffer buffer = new GlMutableBuffer();
        buffer.setSize(32);
        EnumBitField<GlBufferMapFlags> write = EnumBitField.of(GlBufferMapFlags.WRITE);
        ByteBuffer core = BufferMapRangeFunctions.CORE.mapBufferRange(buffer, 4, 8, write);
        assertEquals(8, core.capacity());
        ByteBuffer whole = BufferMapRangeFunctions.MAP_FULL_AND_SLICE.mapBufferRange(buffer, 0, 32, write);
        assertEquals(1 << 16, whole.capacity());
        ByteBuffer sliced = BufferMapRangeFunctions.MAP_FULL_AND_SLICE.mapBufferRange(buffer, 4, 8, EnumBitField.of(GlBufferMapFlags.READ, GlBufferMapFlags.WRITE));
        assertEquals(8, sliced.capacity());
        BufferMapRangeFunctions.MAP_FULL_AND_SLICE.mapBufferRange(buffer, 4, 8, EnumBitField.of(GlBufferMapFlags.READ));
        Mockito.verify(TestGl.gl()).glMapBuffer(GlBufferTarget.ARRAY_BUFFER.getTargetParameter(), 0x88B8);
        Mockito.verify(TestGl.gl()).glMapBuffer(GlBufferTarget.ARRAY_BUFFER.getTargetParameter(), 0x88BA);
        assertThrows(UnsupportedOperationException.class, () -> BufferMapRangeFunctions.MAP_FULL_AND_SLICE.mapBufferRange(buffer, 0, 8, EnumBitField.of(GlBufferMapFlags.EXPLICIT_FLUSH)));
    }
}
