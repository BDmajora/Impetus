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
import org.mockito.Mockito;

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
        assertEquals(101, TestGl.gl().memGetInt(emitter.batch().pBaseVertex + 4));
        assertEquals(24, TestGl.gl().memGetAddress(emitter.batch().pElementPointer + 8));
        emitter.addDrawCommands(data, 0b101, 0);
        assertEquals(5, emitter.batch().size());
        assertEquals(0, TestGl.gl().memGetAddress(emitter.batch().pElementPointer + 3 * 8));
        GlTessellation tessellation = Mockito.mock(GlTessellation.class);
        emitter.executeBatch(commands, tessellation, GlPrimitiveType.TRIANGLES);
        Mockito.verify(TestGl.gl()).glMultiDrawElementsBaseVertex(4, emitter.batch().pElementCount, 0x1405, emitter.batch().pElementPointer, 5, emitter.batch().pBaseVertex);
        emitter.clear();
        assertTrue(emitter.isEmpty());
        emitter.delete();
        SectionRenderDataUnsafe.freeHeap(data);
        assertEquals(7 * 256 + 1, MultiDrawEmitter.MAX_COMMAND_COUNT);
    }

    @Test
    void indirectEmitterWritesGpuCommands() {
        long data = sectionData();
        IndirectMultiDrawEmitter emitter = new IndirectMultiDrawEmitter();
        assertTrue(emitter.isEmpty());
        assertEquals(0, emitter.getIndexBufferSize());
        emitter.addDrawCommands(data, 0b011, -1);
        assertFalse(emitter.isEmpty());
        assertEquals(12, emitter.getIndexBufferSize());
        GlTessellation tessellation = Mockito.mock(GlTessellation.class);
        emitter.executeBatch(commands, tessellation, GlPrimitiveType.TRIANGLES);
        Mockito.verify(commands).uploadData(Mockito.any(), Mockito.anyLong(), Mockito.eq(40L), Mockito.any());
        Mockito.verify(TestGl.gl()).glMultiDrawElementsIndirect(4, 0x1405, 0, 2, 0);
        emitter.clear();
        assertTrue(emitter.isEmpty());
        emitter.delete();
        SectionRenderDataUnsafe.freeHeap(data);
        Mockito.when(TestGl.gl().nmemAlignedAlloc(Mockito.anyLong(), Mockito.anyLong())).thenReturn(0L);
        assertThrows(OutOfMemoryError.class, IndirectMultiDrawEmitter::new);
    }
}
