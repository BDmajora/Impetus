package com.bdmajora.impetus.engine.impl.render.chunk.data;

import com.bdmajora.impetus.engine.impl.common.util.NativeBuffer;
import com.bdmajora.impetus.engine.impl.gl.arena.GlBufferArena;
import com.bdmajora.impetus.engine.impl.gl.arena.GlBufferSegment;
import com.bdmajora.impetus.engine.impl.gl.arena.PendingUpload;
import com.bdmajora.impetus.engine.impl.gl.arena.staging.FallbackStagingBuffer;
import com.bdmajora.impetus.engine.impl.gl.device.CommandList;
import com.bdmajora.impetus.engine.impl.gl.device.GLRenderDevice;
import com.bdmajora.impetus.engine.impl.gl.util.VertexRange;
import com.bdmajora.impetus.engine.impl.model.quad.properties.ModelQuadFacing;
import com.bdmajora.impetus.engine.impl.render.chunk.compile.sorting.QuadPrimitiveType;
import com.bdmajora.testing.Devices;
import com.bdmajora.testing.TestGl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SectionRenderDataStorageTest {
    private GLRenderDevice device;
    private CommandList commands;

    @BeforeEach
    void activate() {
        device = Devices.active();
        commands = device.createCommandList();
    }

    @AfterEach
    void deactivate() {
        device.makeInactive();
    }

    private static GlBufferSegment segment(GlBufferArena arena, CommandList commands, int bytes) {
        PendingUpload upload = PendingUpload.of(new NativeBuffer(bytes));
        arena.upload(commands, new ArrayList<>(List.of(upload)));
        return upload.getResult();
    }

    @Test
    void laysOutFacingRangesInNativeMemory() {
        GlBufferArena vertices = new GlBufferArena(commands, 256, 4, new FallbackStagingBuffer(commands));
        GlBufferArena indices = new GlBufferArena(commands, 256, 4, new FallbackStagingBuffer(commands));
        SectionRenderDataStorage storage = new SectionRenderDataStorage(QuadPrimitiveType.TRIANGULATED);
        assertTrue(storage.isEmpty());
        GlBufferSegment first = segment(vertices, commands, 16 * 4);
        GlBufferSegment vertexSeg = segment(vertices, commands, 8 * 4);
        GlBufferSegment indexSeg = segment(indices, commands, 12 * 4);
        storage.setMeshes(5, vertexSeg, indexSeg, Map.of(ModelQuadFacing.POS_Y, new VertexRange(0, 4), ModelQuadFacing.NEG_Y, new VertexRange(4, 4)));
        assertFalse(storage.isEmpty());
        long data = storage.getDataPointer(5);
        int mask = SectionRenderDataUnsafe.getSliceMask(data);
        assertTrue((mask & (1 << ModelQuadFacing.POS_Y.ordinal())) != 0);
        assertTrue((mask & (1 << ModelQuadFacing.NEG_Y.ordinal())) != 0);
        assertEquals(6, SectionRenderDataUnsafe.getElementCount(data, ModelQuadFacing.POS_Y.ordinal()));
        assertEquals(vertexSeg.getOffset(), SectionRenderDataUnsafe.getVertexOffset(data, ModelQuadFacing.POS_X.ordinal()));
        assertEquals(vertexSeg.getOffset() + 4, SectionRenderDataUnsafe.getVertexOffset(data, ModelQuadFacing.POS_Z.ordinal()));
        assertEquals(indexSeg.getOffset() * 4 + 24, SectionRenderDataUnsafe.getIndexOffset(data, ModelQuadFacing.POS_Z.ordinal()));
        storage.onBufferResized();
        GlBufferSegment resorted = segment(indices, commands, 12 * 4);
        storage.replaceIndexBuffer(5, resorted);
        assertEquals(resorted.getOffset() * 4, SectionRenderDataUnsafe.getIndexOffset(data, 0));
        storage.replaceIndexBuffer(2, segment(indices, commands, 4));
        storage.removeMeshes(5);
        assertTrue(storage.isEmpty());
        assertEquals(0, SectionRenderDataUnsafe.getSliceMask(data));
        storage.removeMeshes(5);
        storage.setMeshes(1, first, null, Map.of());
        storage.delete();
        first.getLength();
        new SectionRenderDataUnsafe();
        Mockito.when(TestGl.gl().nmemCalloc(Mockito.anyLong(), Mockito.anyLong())).thenReturn(0L);
        assertThrows(OutOfMemoryError.class, () -> new SectionRenderDataStorage(QuadPrimitiveType.DIRECT));
    }
}
