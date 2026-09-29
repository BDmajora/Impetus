package com.bdmajora.impetus.engine.impl.gl.device;

import com.bdmajora.impetus.engine.impl.gl.array.GlVertexArray;
import com.bdmajora.impetus.engine.impl.gl.buffer.GlBufferMapFlags;
import com.bdmajora.impetus.engine.impl.gl.buffer.GlBufferMapping;
import com.bdmajora.impetus.engine.impl.gl.buffer.GlBufferStorageFlags;
import com.bdmajora.impetus.engine.impl.gl.buffer.GlBufferTarget;
import com.bdmajora.impetus.engine.impl.gl.buffer.GlBufferUsage;
import com.bdmajora.impetus.engine.impl.gl.buffer.GlImmutableBuffer;
import com.bdmajora.impetus.engine.impl.gl.buffer.GlMutableBuffer;
import com.bdmajora.impetus.engine.impl.gl.sync.GlFence;
import com.bdmajora.impetus.engine.impl.gl.tessellation.GlIndexType;
import com.bdmajora.impetus.engine.impl.gl.tessellation.GlPrimitiveType;
import com.bdmajora.impetus.engine.impl.gl.tessellation.GlTessellation;
import com.bdmajora.impetus.engine.impl.gl.util.EnumBitField;
import com.bdmajora.impetus.engine.impl.gpu.device.GpuDeviceFeature;
import com.bdmajora.testing.TestGl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class GLRenderDeviceTest {
    private final AtomicInteger resets = new AtomicInteger();
    private GLRenderDevice device;

    @BeforeEach
    void activate() {
        GLRenderDevice.VANILLA_STATE_RESETTER = resets::incrementAndGet;
        device = new GLRenderDevice();
    }

    @AfterEach
    void deactivate() {
        device.makeInactive();
    }

    @Test
    void activationGatesAccessAndCapturesTheDevice() {
        assertThrows(IllegalStateException.class, com.bdmajora.testing.Devices.DEFAULT_RESETTER::run);
        assertThrows(IllegalStateException.class, device::createCommandList);
        assertThrows(IllegalStateException.class, device::getGpuDevice);
        device.makeActive();
        device.makeActive();
        assertEquals(1, resets.get());
        assertNotNull(device.createCommandList());
        assertTrue(device.getGpuDevice().supports(GpuDeviceFeature.BUFFER_STORAGE));
        assertNotNull(device.getDeviceFunctions());
        device.makeInactive();
        device.makeInactive();
        assertEquals(2, resets.get());
        GLRenderDevice fresh = new GLRenderDevice();
        fresh.makeActive();
        assertNotNull(fresh.getGpuDevice());
        fresh.makeInactive();
        RenderDevice.enterManagedCode();
        assertNotNull(RenderDevice.INSTANCE.createCommandList());
        RenderDevice.exitManagedCode();
        GLRenderDevice.VANILLA_STATE_RESETTER = () -> {};
    }

    @Test
    void commandListManagesBuffersAndArrays() {
        device.makeActive();
        CommandList list = device.createCommandList();
        GlMutableBuffer buffer = list.createMutableBuffer();
        ByteBuffer data = ByteBuffer.allocateDirect(12);
        list.uploadData(buffer, data, GlBufferUsage.STATIC_DRAW);
        assertEquals(12, buffer.getSize());
        Mockito.verify(TestGl.gl()).glBufferData(GlBufferTarget.ARRAY_BUFFER.getTargetParameter(), data, GlBufferUsage.STATIC_DRAW.getId());
        list.uploadData(buffer, 100L, 24L, GlBufferUsage.DYNAMIC_DRAW);
        assertEquals(24, buffer.getSize());
        list.allocateStorage(buffer, 48, GlBufferUsage.STREAM_DRAW);
        assertEquals(48, buffer.getSize());
        Mockito.verify(TestGl.gl(), Mockito.times(1)).glBindBuffer(GlBufferTarget.ARRAY_BUFFER.getTargetParameter(), buffer.handle());
        list.bindBuffer(GlBufferTarget.ELEMENT_BUFFER, buffer);
        list.bindBuffer(GlBufferTarget.ELEMENT_BUFFER, null);
        Mockito.verify(TestGl.gl()).glBindBuffer(GlBufferTarget.ELEMENT_BUFFER.getTargetParameter(), 0);

        GlMutableBuffer other = list.createMutableBuffer();
        list.copyBufferSubData(buffer, other, 0, 0, 8);
        Mockito.verify(TestGl.gl()).glCopyBufferSubData(0x8F36, 0x8F37, 0, 0, 8);

        GlVertexArray array = new GlVertexArray();
        list.bindVertexArray(array);
        list.bindVertexArray(array);
        Mockito.verify(TestGl.gl(), Mockito.times(1)).glBindVertexArray(array.handle());
        list.unbindVertexArray();
        Mockito.verify(TestGl.gl()).glBindVertexArray(0);
        list.deleteVertexArray(array);
        assertThrows(IllegalStateException.class, array::handle);

        GlFence fence = list.createFence();
        assertNotNull(fence);
        list.flush();
        list.close();
        list.deleteBuffer(other);
        assertThrows(IllegalStateException.class, other::handle);
    }

    @Test
    void mappingsAreValidatedAndTracked() {
        device.makeActive();
        CommandList list = device.createCommandList();
        GlImmutableBuffer buffer = list.createImmutableBuffer(64, EnumBitField.of(GlBufferStorageFlags.MAP_WRITE, GlBufferStorageFlags.PERSISTENT));
        Mockito.verify(TestGl.gl()).glBufferStorage(Mockito.eq(GlBufferTarget.ARRAY_BUFFER.getTargetParameter()), Mockito.eq(64L), Mockito.anyInt());
        GlBufferMapping mapping = list.mapBuffer(buffer, 0, 64, EnumBitField.of(GlBufferMapFlags.WRITE, GlBufferMapFlags.PERSISTENT));
        assertSame(mapping, buffer.getActiveMapping());
        assertThrows(IllegalStateException.class, () -> list.mapBuffer(buffer, 0, 64, EnumBitField.of(GlBufferMapFlags.WRITE)));
        list.flushMappedRange(mapping, 0, 16);
        Mockito.verify(TestGl.gl()).glFlushMappedBufferRange(GlBufferTarget.COPY_READ_BUFFER.getTargetParameter(), 0, 16);
        list.unmap(mapping);
        assertNull(buffer.getActiveMapping());
        assertTrue(mapping.isDisposed());
        assertThrows(IllegalStateException.class, () -> list.unmap(mapping));
        assertThrows(IllegalStateException.class, () -> list.flushMappedRange(mapping, 0, 1));

        assertThrows(IllegalStateException.class, () -> list.mapBuffer(buffer, 0, 64, EnumBitField.of(GlBufferMapFlags.READ)));
        GlImmutableBuffer readOnly = list.createImmutableBuffer(8, EnumBitField.of(GlBufferStorageFlags.MAP_READ));
        assertThrows(IllegalStateException.class, () -> list.mapBuffer(readOnly, 0, 8, EnumBitField.of(GlBufferMapFlags.WRITE)));
        assertThrows(IllegalArgumentException.class, () -> list.mapBuffer(readOnly, 0, 8, EnumBitField.of(GlBufferMapFlags.PERSISTENT, GlBufferMapFlags.READ)));
        GlMutableBuffer mutable = list.createMutableBuffer();
        assertThrows(IllegalStateException.class, () -> list.mapBuffer(mutable, 0, 8, EnumBitField.of(GlBufferMapFlags.PERSISTENT)));
        Mockito.when(TestGl.gl().glMapBufferRange(Mockito.anyInt(), Mockito.anyLong(), Mockito.anyLong(), Mockito.anyInt())).thenReturn(null);
        assertThrows(RuntimeException.class, () -> list.mapBuffer(mutable, 0, 8, EnumBitField.of(GlBufferMapFlags.WRITE)));
        Mockito.reset(TestGl.gl());
        GlBufferMapping second = list.mapBuffer(readOnly, 0, 8, EnumBitField.of(GlBufferMapFlags.READ));
        list.deleteBuffer(readOnly);
        assertTrue(second.isDisposed());
    }

    @Test
    void drawCommandListsDrawAndUnbind() {
        device.makeActive();
        CommandList list = device.createCommandList();
        GlTessellation tessellation = Mockito.mock(GlTessellation.class);
        DrawCommandList draw = list.beginTessellating(tessellation);
        Mockito.verify(tessellation).bind(list);
        MultiDrawBatch batch = new MultiDrawBatch(4);
        assertEquals(4, batch.capacity());
        assertTrue(batch.isEmpty());
        TestGl.gl().memPutInt(batch.pElementCount, 6);
        TestGl.gl().memPutInt(batch.pElementCount + 4, 12);
        batch.size = 2;
        assertEquals(2, batch.size());
        assertFalse(batch.isEmpty());
        assertEquals(12, batch.getIndexBufferSize());
        draw.multiDrawElementsBaseVertex(batch, GlPrimitiveType.TRIANGLES, GlIndexType.UNSIGNED_INT);
        Mockito.verify(TestGl.gl()).glMultiDrawElementsBaseVertex(4, batch.pElementCount, 0x1405, batch.pElementPointer, 2, batch.pBaseVertex);
        GlMutableBuffer indirect = list.createMutableBuffer();
        draw.multiDrawElementsIndirect(indirect, 3, GlPrimitiveType.TRIANGLES, GlIndexType.UNSIGNED_SHORT);
        Mockito.verify(TestGl.gl()).glMultiDrawElementsIndirect(4, 0x1403, 0, 3, 0);
        batch.clear();
        assertTrue(batch.isEmpty());
        batch.delete();
        draw.close();
        Mockito.verify(tessellation).unbind(list);
        draw.flush();
        Mockito.verify(tessellation, Mockito.times(1)).unbind(list);
        list.deleteTessellation(tessellation);
        Mockito.verify(tessellation).delete(list);
    }

    @Test
    void batchAllocationFailuresAreReported() {
        long real = TestGl.gl().nmemAlloc(64);
        Mockito.when(TestGl.gl().nmemAlignedAlloc(Mockito.anyLong(), Mockito.anyLong())).thenReturn(0L);
        assertThrows(OutOfMemoryError.class, () -> new MultiDrawBatch(1));
        Mockito.when(TestGl.gl().nmemAlignedAlloc(Mockito.anyLong(), Mockito.anyLong())).thenReturn(real, 0L);
        Mockito.doNothing().when(TestGl.gl()).nmemAlignedFree(Mockito.anyLong());
        assertThrows(OutOfMemoryError.class, () -> new MultiDrawBatch(1));
        Mockito.when(TestGl.gl().nmemAlignedAlloc(Mockito.anyLong(), Mockito.anyLong())).thenReturn(real, real, 0L);
        assertThrows(OutOfMemoryError.class, () -> new MultiDrawBatch(1));
        TestGl.gl().nmemFree(real);
    }
}
