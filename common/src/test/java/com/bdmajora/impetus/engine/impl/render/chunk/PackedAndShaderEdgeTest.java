package com.bdmajora.impetus.engine.impl.render.chunk;

import com.bdmajora.impetus.engine.impl.gl.buffer.GlMutableBuffer;
import com.bdmajora.impetus.engine.impl.gl.device.CommandList;
import com.bdmajora.impetus.engine.impl.render.chunk.compile.sorting.ChunkPrimitiveType;
import com.bdmajora.impetus.engine.impl.render.chunk.shader.ChunkFogMode;
import com.bdmajora.impetus.engine.impl.render.chunk.shader.ChunkShaderOptions;
import com.bdmajora.impetus.engine.impl.render.chunk.shader.ChunkShaderTextureSlot;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

// Tests packed metadata and small shader and buffer classes.
class PackedAndShaderEdgeTest {
    // Round trips every field and leaves the others unchanged.
    @Test
    void metadataFields() {
        long packed = 0L;
        packed = PackedSectionMetadata.withVisibilityData(packed, -1L);
        assertEquals(PackedSectionMetadata.VISIBILITY_MASK, PackedSectionMetadata.getVisibilityData(packed));
        assertEquals(0, PackedSectionMetadata.getVisualsFlags(packed));
        packed = PackedSectionMetadata.withVisualsFlags(packed, 0xFFFF);
        assertEquals(0xF, PackedSectionMetadata.getVisualsFlags(packed));
        assertEquals(PackedSectionMetadata.VISIBILITY_MASK, PackedSectionMetadata.getVisibilityData(packed));
        for (ChunkUpdateType type : ChunkUpdateType.VALUES) {
            long with = PackedSectionMetadata.withPendingUpdate(packed, type);
            assertSame(type, PackedSectionMetadata.getPendingUpdate(with));
            assertSame(type, PackedSectionMetadata.getCompactPendingUpdate(PackedSectionMetadata.toCompactMeta(with)));
            assertEquals(0xF, PackedSectionMetadata.getVisualsFlags(with));
            assertNull(PackedSectionMetadata.getPendingUpdate(PackedSectionMetadata.withPendingUpdate(with, null)));
        }
        assertFalse(PackedSectionMetadata.isBuildInFlight(packed));
        long flying = PackedSectionMetadata.withBuildInFlight(packed, true);
        assertTrue(PackedSectionMetadata.isBuildInFlight(flying));
        assertTrue(PackedSectionMetadata.isCompactBuildInFlight(PackedSectionMetadata.toCompactMeta(flying)));
        assertEquals(packed, PackedSectionMetadata.withBuildInFlight(flying, false));
        assertTrue(PackedSectionMetadata.hasOccluderData(PackedSectionMetadata.withHasOccluderData(0L, true)));
        assertFalse(PackedSectionMetadata.hasOccluderData(PackedSectionMetadata.withHasOccluderData(-1L, false)));
        assertEquals(0xF, PackedSectionMetadata.getCompactVisualsFlags(PackedSectionMetadata.toCompactMeta(packed)));
        assertEquals(0, PackedSectionMetadata.withVisibilityData(Long.MIN_VALUE, 0L) & PackedSectionMetadata.VISIBILITY_MASK);
        assertNotEquals(0, PackedSectionMetadata.GRAPH_INPUT_MASK & PackedSectionMetadata.VISIBILITY_MASK);
    }

    // Maps GL fog constants and rejects unknown ones.
    @Test
    void fogModes() {
        assertSame(ChunkFogMode.NONE, ChunkFogMode.fromGLMode(0));
        assertSame(ChunkFogMode.EXP, ChunkFogMode.fromGLMode(GL11.GL_EXP));
        assertSame(ChunkFogMode.EXP2, ChunkFogMode.fromGLMode(GL11.GL_EXP2));
        assertSame(ChunkFogMode.SMOOTH, ChunkFogMode.fromGLMode(GL11.GL_LINEAR));
        assertThrows(UnsupportedOperationException.class, () -> ChunkFogMode.fromGLMode(-1));
        assertThrows(UnsupportedOperationException.class, () -> ChunkFogMode.fromGLMode(Integer.MAX_VALUE));
        assertTrue(ChunkFogMode.NONE.getDefines().isEmpty());
        assertEquals(List.of("USE_FOG", "USE_FOG_EXP"), ChunkFogMode.EXP.getDefines());
        assertEquals(GL20.GL_EXP == GL11.GL_EXP, true);
        assertEquals(2, ChunkShaderTextureSlot.VALUES.length);
    }

    // Rejects null parts of shader options and the pass configuration.
    @Test
    void nullRefusals() {
        assertThrows(NullPointerException.class, () -> new ChunkShaderOptions(null, null));
        assertThrows(NullPointerException.class, () -> new ChunkShaderOptions(List.of(), null));
        assertThrows(NullPointerException.class, () -> new RenderPassConfiguration<String>(null, Map.of(), null, null, null));
        assertThrows(NullPointerException.class, () -> new RenderPassConfiguration<String>(Map.of(), Map.of(), null, null, null));
    }

    // Refuses to grow past the int range and never grows for a smaller request.
    @Test
    void sharedIndexBufferGrowth() {
        CommandList list = Mockito.mock(CommandList.class);
        Mockito.when(list.createMutableBuffer()).thenReturn(Mockito.mock(GlMutableBuffer.class));
        ChunkPrimitiveType type = Mockito.mock(ChunkPrimitiveType.class);
        Mockito.when(type.getIndexBufferElementsPerPrimitive()).thenReturn(6);
        Mockito.when(type.getIndexBufferSize(Mockito.anyInt())).thenAnswer(inv -> Math.multiplyExact((int) inv.getArgument(0), 24));
        assertThrows(NullPointerException.class, () -> new SharedQuadIndexBuffer(list, null));
        SharedQuadIndexBuffer buffer = new SharedQuadIndexBuffer(list, type);
        assertThrows(ArithmeticException.class, () -> buffer.ensureCapacity(list, Integer.MAX_VALUE));
        buffer.ensureCapacity(list, 0);
        buffer.ensureCapacity(list, -6);
        Mockito.verify(list, Mockito.never()).allocateStorage(Mockito.any(), Mockito.anyInt(), Mockito.any());
        assertNotNull(buffer.getBufferObject());
        buffer.delete(list);
    }
}
