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
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.nio.ByteBuffer;
import org.lwjgl.system.MemoryUtil;

import static org.junit.jupiter.api.Assertions.*;

class GLRenderDeviceTest {
    private GLRenderDevice device;

    @BeforeEach
    void activate() {
        device = new GLRenderDevice();
    }

    @AfterEach
    void deactivate() {
        device.makeInactive();
    }

    @Test
    void activationGatesAccessAndCapturesTheDevice() {
        int arrayBuffer = GlBufferTarget.ARRAY_BUFFER.getTargetParameter();
        assertThrows(IllegalStateException.class, device::createCommandList);
        assertThrows(IllegalStateException.class, device::getGpuDevice);
        // Only a real transition touches GL: each one leaves ARRAY_BUFFER unbound for the host
        device.makeActive();
        device.makeActive();
        Mockito.verify(TestGl.gl(), Mockito.times(1)).glBindBuffer(arrayBuffer, 0);
        assertNotNull(device.createCommandList());
        assertTrue(device.getGpuDevice().supports(GpuDeviceFeature.BUFFER_STORAGE));
        assertSame(device.getGpuDevice(), device.getGpuDevice());
        assertNotNull(device.getDeviceFunctions());
        device.makeInactive();
        device.makeInactive();
        Mockito.verify(TestGl.gl(), Mockito.times(2)).glBindBuffer(arrayBuffer, 0);
        RenderDevice.enterManagedCode();
        assertNotNull(RenderDevice.INSTANCE.createCommandList());
        RenderDevice.exitManagedCode();
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
        DirectMultiDrawBatch batch = new DirectMultiDrawBatch(4);
        batch.appendDrawCommand(0, 6, 0L);
        batch.appendDrawCommand(4, 12, 0L);
        draw.multiDrawElementsBaseVertex(batch, GlPrimitiveType.TRIANGLES, GlIndexType.UNSIGNED_INT);
        Mockito.verify(TestGl.gl()).glMultiDrawElementsBaseVertex(4, batch.pElementCount, 0x1405, batch.pElementPointer, 2, batch.pBaseVertex);
        draw.multiDrawElementsIndirect(64L, 3, GlPrimitiveType.TRIANGLES, GlIndexType.UNSIGNED_SHORT);
        Mockito.verify(TestGl.gl()).glMultiDrawElementsIndirect(4, 0x1403, 64L, 3, 0);
        batch.delete();
        draw.close();
        Mockito.verify(tessellation).unbind(list);
        draw.flush();
        Mockito.verify(tessellation, Mockito.times(1)).unbind(list);
        list.deleteTessellation(tessellation);
        Mockito.verify(tessellation).delete(list);
    }

    @Test
    void directBatchesKeepClientSideArraysAndSkipEmptyCommands() {
        device.makeActive();
        CommandList list = device.createCommandList();
        DirectMultiDrawBatch batch = new DirectMultiDrawBatch(4);
        // One spare slot past the requested capacity, for the branchless append
        assertEquals(5, batch.capacity());
        assertTrue(batch.isEmpty());
        batch.appendDrawCommand(100, 6, 24L);
        batch.appendDrawCommand(200, 0, 48L);
        assertEquals(1, batch.size());
        batch.appendDrawCommand(300, 12, 96L);
        assertEquals(2, batch.size());
        assertEquals(12, batch.getIndexBufferSize());
        assertEquals(300, MemoryUtil.memGetInt(batch.pBaseVertex + 4));
        assertEquals(96L, MemoryUtil.memGetAddress(batch.pElementPointer + 8));
        batch.mergeIntoLastCommand(6);
        assertEquals(18, MemoryUtil.memGetInt(batch.pElementCount + 4));
        assertEquals(18, batch.getIndexBufferSize());
        batch.upload(list);
        Mockito.verify(TestGl.gl(), Mockito.never()).glBufferData(Mockito.anyInt(), Mockito.anyLong(), Mockito.anyLong(), Mockito.anyInt());
        GlTessellation tessellation = Mockito.mock(GlTessellation.class);
        batch.execute(list, tessellation, GlPrimitiveType.TRIANGLES);
        Mockito.verify(TestGl.gl()).glMultiDrawElementsBaseVertex(4, batch.pElementCount, 0x1405, batch.pElementPointer, 2, batch.pBaseVertex);
        Mockito.verify(tessellation).unbind(list);
        batch.clear();
        assertTrue(batch.isEmpty());
        assertEquals(0, batch.getIndexBufferSize());
        batch.delete();
        assertEquals((7 * 256) + 1, MultiDrawBatch.MAX_COMMAND_COUNT);
    }

    @Test
    void indirectBatchesUploadTheirCommandsOnceAndDrawFromTheBuffer() {
        device.makeActive();
        CommandList list = device.createCommandList();
        IndirectMultiDrawBatch batch = new IndirectMultiDrawBatch(3);
        batch.appendDrawCommand(100, 6, 24L);
        batch.appendDrawCommand(200, 0, 0L);
        batch.appendDrawCommand(300, 12, 0L);
        batch.mergeIntoLastCommand(6);
        assertEquals(2, batch.size());
        assertEquals(18, batch.getIndexBufferSize());
        // count, instanceCount, firstIndex, baseVertex, baseInstance for each command
        Mockito.doAnswer(inv -> {
            long commands = inv.getArgument(2);
            assertEquals(6, MemoryUtil.memGetInt(commands));
            assertEquals(1, MemoryUtil.memGetInt(commands + 4));
            assertEquals(6, MemoryUtil.memGetInt(commands + 8));
            assertEquals(100, MemoryUtil.memGetInt(commands + 12));
            assertEquals(18, MemoryUtil.memGetInt(commands + 20));
            assertEquals(300, MemoryUtil.memGetInt(commands + 32));
            return null;
        }).when(TestGl.gl()).glBufferData(Mockito.anyInt(), Mockito.eq(40L), Mockito.anyLong(), Mockito.eq(GlBufferUsage.STATIC_DRAW.getId()));
        batch.upload(list);
        Mockito.verify(TestGl.gl()).glBufferData(Mockito.anyInt(), Mockito.eq(40L), Mockito.anyLong(), Mockito.eq(GlBufferUsage.STATIC_DRAW.getId()));
        GlTessellation tessellation = Mockito.mock(GlTessellation.class);
        batch.execute(list, tessellation, GlPrimitiveType.TRIANGLES);
        Mockito.verify(TestGl.gl()).glMultiDrawElementsIndirect(4, 0x1405, 0L, 2, 0);
        Mockito.verify(TestGl.gl()).glBindBuffer(GlBufferTarget.DRAW_INDIRECT_BUFFER.getTargetParameter(), 0);
        // The CPU copy went with the upload, so only the buffer is left to free
        batch.delete();
        IndirectMultiDrawBatch neverUploaded = new IndirectMultiDrawBatch(1);
        neverUploaded.delete();
        Mockito.verify(TestGl.gl(), Mockito.times(2)).glDeleteBuffers(Mockito.anyInt());
    }

    @Test
    void batchAllocationFailuresAreReported() {
        long real = MemoryUtil.nmemAlloc(64);
        try (MockedStatic<MemoryUtil> memory = Mockito.mockStatic(MemoryUtil.class, Mockito.CALLS_REAL_METHODS)) {
            memory.when(() -> MemoryUtil.nmemAlignedAlloc(Mockito.anyLong(), Mockito.anyLong())).thenReturn(0L);
            assertThrows(OutOfMemoryError.class, () -> new DirectMultiDrawBatch(1));
            assertThrows(OutOfMemoryError.class, () -> new IndirectMultiDrawBatch(1));
            memory.when(() -> MemoryUtil.nmemAlignedAlloc(Mockito.anyLong(), Mockito.anyLong())).thenReturn(real, 0L);
            memory.when(() -> MemoryUtil.nmemAlignedFree(Mockito.anyLong())).then(inv -> null);
            assertThrows(OutOfMemoryError.class, () -> new DirectMultiDrawBatch(1));
            memory.when(() -> MemoryUtil.nmemAlignedAlloc(Mockito.anyLong(), Mockito.anyLong())).thenReturn(real, real, 0L);
            assertThrows(OutOfMemoryError.class, () -> new DirectMultiDrawBatch(1));
        }
        MemoryUtil.nmemFree(real);
    }
}
