package com.bdmajora.impetus.engine.impl.gl.functions;

import com.bdmajora.impetus.engine.impl.gl.buffer.GlBufferMapFlags;
import com.bdmajora.impetus.engine.impl.gl.buffer.GlBufferMapping;
import com.bdmajora.impetus.engine.impl.gl.buffer.GlBufferStorageFlags;
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
        assertEquals(BufferStorageFunctions.CORE, BufferStorageFunctions.pickBest(device));
        assertEquals(MultidrawFunctions.CORE, MultidrawFunctions.pickBest(device));
        assertEquals(BufferCopyFunctions.CORE, BufferCopyFunctions.pickBest(device));
        assertEquals(BufferMapRangeFunctions.CORE, BufferMapRangeFunctions.pickBest(device));
        DeviceFunctions functions = new DeviceFunctions(device);
        assertEquals(BufferStorageFunctions.CORE, functions.bufferStorageFunctions());
        versions(false);
        assertEquals(BufferStorageFunctions.NONE, BufferStorageFunctions.pickBest(device));
        assertEquals(MultidrawFunctions.NONE, MultidrawFunctions.pickBest(device));
        assertEquals(BufferCopyFunctions.PIXEL_PACK, BufferCopyFunctions.pickBest(device));
        assertEquals(BufferMapRangeFunctions.MAP_FULL_AND_SLICE, BufferMapRangeFunctions.pickBest(device));
        Mockito.when(TestGl.gl().isExtensionSupported(GLExtension.ARB_draw_elements_base_vertex)).thenReturn(true);
        assertEquals(MultidrawFunctions.FALLBACK, MultidrawFunctions.pickBest(device));
    }

    @Test
    void storageAndMultidrawImplementations() {
        EnumBitField<GlBufferStorageFlags> flags = EnumBitField.of(GlBufferStorageFlags.MAP_WRITE);
        BufferStorageFunctions.CORE.createBufferStorage(GlBufferTarget.ARRAY_BUFFER, 64, flags);
        Mockito.verify(TestGl.gl()).glBufferStorage(GlBufferTarget.ARRAY_BUFFER.getTargetParameter(), 64, flags.getBitField());
        assertThrows(UnsupportedOperationException.class, () -> BufferStorageFunctions.NONE.createBufferStorage(GlBufferTarget.ARRAY_BUFFER, 64, flags));
        assertThrows(UnsupportedOperationException.class, () -> MultidrawFunctions.NONE.multiDrawElementsBaseVertex(4, 0, 0, 0, 0, 0));
        MultidrawFunctions.CORE.multiDrawElementsBaseVertex(4, 1, 2, 3, 4, 5);
        Mockito.verify(TestGl.gl()).glMultiDrawElementsBaseVertex(4, 1, 2, 3, 4, 5);

        long counts = TestGl.gl().nmemCalloc(2, 4);
        long pointers = TestGl.gl().nmemCalloc(2, 8);
        long base = TestGl.gl().nmemCalloc(2, 4);
        TestGl.gl().memPutInt(counts, 6);
        TestGl.gl().memPutInt(counts + 4, 0);
        TestGl.gl().memPutAddress(pointers, 100);
        TestGl.gl().memPutInt(base, 7);
        MultidrawFunctions.FALLBACK.multiDrawElementsBaseVertex(4, counts, 2, pointers, 2, base);
        Mockito.verify(TestGl.gl()).glDrawElementsBaseVertex(4, 6, 2, 100, 7);
        Mockito.verify(TestGl.gl(), Mockito.times(1)).glDrawElementsBaseVertex(Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt(), Mockito.anyLong(), Mockito.anyInt());
        TestGl.gl().nmemFree(counts);
        TestGl.gl().nmemFree(pointers);
        TestGl.gl().nmemFree(base);
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
        long srcMem = TestGl.gl().nmemCalloc(1, 16);
        long dstMem = TestGl.gl().nmemCalloc(1, 16);
        TestGl.gl().memPutInt(srcMem + 4, 42);
        Mockito.when(TestGl.gl().nglMapBuffer(GlBufferTarget.PIXEL_PACK_BUFFER.getTargetParameter(), 0x88B8)).thenReturn(srcMem);
        Mockito.when(TestGl.gl().nglMapBuffer(GlBufferTarget.PIXEL_UNPACK_BUFFER.getTargetParameter(), 0x88B9)).thenReturn(dstMem);
        BufferCopyFunctions.PIXEL_PACK.copyBufferSubData(list, src, dst, 4, 8, 4);
        assertEquals(42, TestGl.gl().memGetInt(dstMem + 8));
        Mockito.verify(list).bindBuffer(GlBufferTarget.PIXEL_PACK_BUFFER, null);

        Mockito.when(TestGl.gl().nglMapBuffer(GlBufferTarget.PIXEL_UNPACK_BUFFER.getTargetParameter(), 0x88B9)).thenReturn(0L);
        assertThrows(IllegalStateException.class, () -> BufferCopyFunctions.PIXEL_PACK.copyBufferSubData(list, src, dst, 0, 0, 4));
        Mockito.when(TestGl.gl().nglMapBuffer(GlBufferTarget.PIXEL_PACK_BUFFER.getTargetParameter(), 0x88B8)).thenReturn(0L);
        assertThrows(IllegalStateException.class, () -> BufferCopyFunctions.PIXEL_PACK.copyBufferSubData(list, src, dst, 0, 0, 4));
        src.setActiveMapping(new GlBufferMapping(src, ByteBuffer.allocate(1)));
        assertThrows(IllegalStateException.class, () -> BufferCopyFunctions.PIXEL_PACK.copyBufferSubData(list, src, dst, 0, 0, 4));
        TestGl.gl().nmemFree(srcMem);
        TestGl.gl().nmemFree(dstMem);
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
