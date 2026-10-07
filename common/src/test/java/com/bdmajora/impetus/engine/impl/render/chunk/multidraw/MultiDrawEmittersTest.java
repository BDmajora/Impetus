package com.bdmajora.impetus.engine.impl.render.chunk.multidraw;

import com.bdmajora.impetus.engine.impl.gl.device.CommandList;
import com.bdmajora.impetus.engine.impl.gl.device.GLRenderDevice;
import com.bdmajora.impetus.engine.impl.gl.tessellation.GlPrimitiveType;
import com.bdmajora.impetus.engine.impl.gl.tessellation.GlTessellation;
import com.bdmajora.impetus.engine.impl.render.chunk.data.SectionRenderDataUnsafe;
import com.bdmajora.testing.Devices;
import com.bdmajora.testing.TestGl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.lwjgl.system.MemoryUtil;

import static org.junit.jupiter.api.Assertions.*;

class MultiDrawEmittersTest {
    private GLRenderDevice device;
    private CommandList commands;

    @BeforeEach
    void activate() {
        device = Devices.active();
        commands = Mockito.spy(device.createCommandList());
    }

    @AfterEach
    void deactivate() {
        device.makeInactive();
    }

    // Section data with three facings populated
    private static long sectionData() {
        long data = SectionRenderDataUnsafe.allocateHeap(1);
        for (int facing = 0; facing < 3; facing++) {
            SectionRenderDataUnsafe.setVertexOffset(data, facing, 100 + facing);
            SectionRenderDataUnsafe.setElementCount(data, facing, 6 * (facing + 1));
            SectionRenderDataUnsafe.setIndexOffset(data, facing, 24 * facing);
        }
        return data;
    }

    @Test
    void directEmitterFillsABatch() {
        long data = sectionData();
        DirectMultiDrawEmitter emitter = new DirectMultiDrawEmitter();
        assertTrue(emitter.isEmpty());
        emitter.addDrawCommands(data, 0b111, -1);
        assertFalse(emitter.isEmpty());
        assertEquals(3, emitter.batch().size());
        assertEquals(18, emitter.getIndexBufferSize());
        assertEquals(101, MemoryUtil.memGetInt(emitter.batch().pBaseVertex + 4));
        assertEquals(24, MemoryUtil.memGetAddress(emitter.batch().pElementPointer + 8));
        emitter.addDrawCommands(data, 0b101, 0);
        assertEquals(5, emitter.batch().size());
        assertEquals(0, MemoryUtil.memGetAddress(emitter.batch().pElementPointer + 3 * 8));
        GlTessellation tessellation = Mockito.mock(GlTessellation.class);
        emitter.executeBatch(commands, tessellation, GlPrimitiveType.TRIANGLES);
        Mockito.verify(TestGl.gl()).glMultiDrawElementsBaseVertex(4, emitter.batch().pElementCount, 0x1405, emitter.batch().pElementPointer, 5, emitter.batch().pBaseVertex);
        emitter.clear();
        assertTrue(emitter.isEmpty());
        emitter.delete(commands);
        SectionRenderDataUnsafe.freeHeap(data);
        assertEquals(7 * 256 + 1, MultiDrawEmitter.MAX_COMMAND_COUNT);
    }

    @Test
    void indirectEmitterDrawsFromAFencedCommandRing() {
        long data = sectionData();
        IndirectMultiDrawEmitter emitter = new IndirectMultiDrawEmitter();
        assertTrue(emitter.isEmpty());
        assertEquals(0, emitter.getIndexBufferSize());
        emitter.addDrawCommands(data, 0b011, -1);
        assertFalse(emitter.isEmpty());
        assertEquals(12, emitter.getIndexBufferSize());
        GlTessellation tessellation = Mockito.mock(GlTessellation.class);
        emitter.executeBatch(commands, tessellation, GlPrimitiveType.TRIANGLES);
        // The ring is created and persistently mapped once, then each batch lands right after the last
        Mockito.verify(commands).createImmutableBuffer(Mockito.eq((long) IndirectMultiDrawEmitter.RING_BYTES), Mockito.any());
        Mockito.verify(TestGl.gl()).glMultiDrawElementsIndirect(4, 0x1405, 0L, 2, 0);
        emitter.executeBatch(commands, tessellation, GlPrimitiveType.TRIANGLES);
        Mockito.verify(TestGl.gl()).glMultiDrawElementsIndirect(4, 0x1405, 40L, 2, 0);
        emitter.finishPass(commands);
        // Nothing new since the fence: no second fence, and the signalled one is reclaimed
        emitter.finishPass(commands);
        Mockito.verify(commands, Mockito.times(1)).createFence();
        emitter.clear();
        assertTrue(emitter.isEmpty());
        emitter.delete(commands);
        Mockito.verify(commands).deleteBuffer(Mockito.any());
        SectionRenderDataUnsafe.freeHeap(data);
        try (MockedStatic<MemoryUtil> memory = Mockito.mockStatic(MemoryUtil.class, Mockito.CALLS_REAL_METHODS)) {
            memory.when(() -> MemoryUtil.nmemAlignedAlloc(Mockito.anyLong(), Mockito.anyLong())).thenReturn(0L);
            assertThrows(OutOfMemoryError.class, IndirectMultiDrawEmitter::new);
        }
    }

    @Test
    void aFullRingWrapsAndWaitsOnlyWhenItMust() {
        long data = sectionData();
        // Room for two 40-byte batches and a 20-byte tail
        IndirectMultiDrawEmitter emitter = new IndirectMultiDrawEmitter(100);
        emitter.addDrawCommands(data, 0b011, 0);
        GlTessellation tessellation = Mockito.mock(GlTessellation.class);
        emitter.executeBatch(commands, tessellation, GlPrimitiveType.TRIANGLES);
        emitter.executeBatch(commands, tessellation, GlPrimitiveType.TRIANGLES);
        // A third batch in the same pass has nothing fenced to wait for, so the GPU is drained and the ring starts over
        emitter.executeBatch(commands, tessellation, GlPrimitiveType.TRIANGLES);
        Mockito.verify(TestGl.gl()).glFinish();
        Mockito.verify(TestGl.gl(), Mockito.times(2)).glMultiDrawElementsIndirect(4, 0x1405, 0L, 2, 0);

        // With the fence still pending, the next wrap waits on it from the CPU
        Mockito.doAnswer(inv -> {
            inv.<java.nio.IntBuffer>getArgument(2).put(0, 1);
            return 0x9118;
        }).when(TestGl.gl()).glGetSynci(Mockito.anyLong(), Mockito.anyInt(), Mockito.any());
        emitter.finishPass(commands);
        emitter.executeBatch(commands, tessellation, GlPrimitiveType.TRIANGLES);
        emitter.executeBatch(commands, tessellation, GlPrimitiveType.TRIANGLES);
        Mockito.verify(TestGl.gl(), Mockito.atLeastOnce()).glClientWaitSync(Mockito.anyLong(), Mockito.anyInt(), Mockito.anyLong());
        emitter.finishPass(commands);
        // Pending fences go with the emitter
        emitter.delete(commands);
        SectionRenderDataUnsafe.freeHeap(data);
    }

    @Test
    void theIndirectEmitterIsChosenOnlyWhenAskedForAndSupported() {
        assertInstanceOf(IndirectMultiDrawEmitter.class, MultiDrawEmitter.create(device, true));
        assertInstanceOf(DirectMultiDrawEmitter.class, MultiDrawEmitter.create(device, false));
        var gpu = Mockito.mock(com.bdmajora.impetus.engine.impl.gpu.device.GpuDevice.class);
        var limited = Mockito.mock(com.bdmajora.impetus.engine.impl.gl.device.RenderDevice.class);
        Mockito.when(limited.getGpuDevice()).thenReturn(gpu);
        Mockito.when(gpu.supports(com.bdmajora.impetus.engine.impl.gpu.device.GpuDeviceFeature.MULTI_DRAW_INDIRECT)).thenReturn(true);
        assertInstanceOf(DirectMultiDrawEmitter.class, MultiDrawEmitter.create(limited, true));
        Mockito.when(gpu.supports(com.bdmajora.impetus.engine.impl.gpu.device.GpuDeviceFeature.MULTI_DRAW_INDIRECT)).thenReturn(false);
        assertInstanceOf(DirectMultiDrawEmitter.class, MultiDrawEmitter.create(limited, true));
    }
}
