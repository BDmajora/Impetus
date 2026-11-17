package com.bdmajora.impetus.engine.impl.render.chunk.vertex;

import com.bdmajora.impetus.engine.impl.render.chunk.sprite.GenericSectionSpriteTicker;
import com.bdmajora.impetus.engine.impl.render.chunk.sprite.SpriteTransparencyLevel;
import com.bdmajora.impetus.engine.impl.render.chunk.terrain.material.parameters.AlphaCutoffParameter;
import com.bdmajora.impetus.engine.impl.render.chunk.terrain.material.parameters.MaterialParameters;
import com.bdmajora.impetus.engine.impl.render.chunk.vertex.builder.ChunkMeshBufferBuilder;
import com.bdmajora.impetus.engine.impl.render.chunk.vertex.format.ChunkVertexEncoder;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// Tests the mesh buffer builder and the small sprite and material helpers.
class VertexBuilderEdgeTest {
    // Encoder that advances the pointer by the stride and writes a marker.
    private static ChunkVertexEncoder encoder(int stride, boolean fail) {
        ChunkVertexEncoder encoder = Mockito.mock(ChunkVertexEncoder.class);
        Mockito.when(encoder.supportsBilinearCorrection()).thenReturn(false);
        Mockito.when(encoder.write(Mockito.anyLong(), Mockito.any(), Mockito.any(), Mockito.anyInt())).thenAnswer(inv -> {
            if (fail) {
                throw new IllegalStateException("encode");
            }
            long ptr = inv.getArgument(0);
            org.lwjgl.system.MemoryUtil.memPutInt(ptr, 0x1234);
            return ptr + stride;
        });
        return encoder;
    }

    // Writes quads and grows past the initial capacity.
    @Test
    void pushAndGrow() {
        ChunkMeshBufferBuilder builder = new ChunkMeshBufferBuilder(encoder(16, false), 16, 64, false);
        assertTrue(builder.isEmpty());
        assertThrows(IllegalStateException.class, builder::slice);
        assertNull(builder.getSortState());
        builder.start(3);
        for (int i = 0; i < 100; i++) {
            builder.push(ChunkVertexEncoder.Vertex.uninitializedQuad(), null);
        }
        assertEquals(400, builder.count());
        assertEquals(400 * 16, builder.slice().remaining());
        builder.start(4);
        assertTrue(builder.isEmpty());
        builder.destroy();
        builder.destroy();
        assertEquals(0, builder.count());
        builder.push(ChunkVertexEncoder.Vertex.uninitializedQuad(), null);
        assertEquals(4, builder.count());
        builder.destroy();
    }

    // Rejects bad constructor arguments.
    @Test
    void constructorRefusals() {
        ChunkVertexEncoder encoder = encoder(16, false);
        assertThrows(IllegalArgumentException.class, () -> new ChunkMeshBufferBuilder(encoder, 0, 64, false));
        assertThrows(IllegalArgumentException.class, () -> new ChunkMeshBufferBuilder(encoder, -4, 64, false));
        assertThrows(IllegalArgumentException.class, () -> new ChunkMeshBufferBuilder(encoder, 16, -1, false));
        assertThrows(NullPointerException.class, () -> new ChunkMeshBufferBuilder(null, 16, 64, false));
        new ChunkMeshBufferBuilder(encoder, 16, 0, true).destroy();
    }

    // Checks the encoder failure.
    @Test
    void encoderFailureKeepsState() {
        ChunkMeshBufferBuilder failing = new ChunkMeshBufferBuilder(encoder(16, true), 16, 64, true);
        assertThrows(IllegalStateException.class, () -> failing.push(ChunkVertexEncoder.Vertex.uninitializedQuad(), null));
        assertEquals(0, failing.count());
        failing.destroy();
        ChunkMeshBufferBuilder sorting = new ChunkMeshBufferBuilder(encoder(16, false), 16, 64, true);
        sorting.push(ChunkVertexEncoder.Vertex.uninitializedQuad(), null);
        assertNotNull(sorting.getSortState());
        sorting.resetSortState();
        sorting.destroy();
    }

    // Reports a vertex as text and creates four distinct vertices.
    @Test
    void vertexHelpers() {
        ChunkVertexEncoder.Vertex[] quad = ChunkVertexEncoder.Vertex.uninitializedQuad();
        assertEquals(4, quad.length);
        assertNotSame(quad[0], quad[1]);
        quad[0].x = 1.5f;
        quad[0].color = 0xFF00FF00;
        assertTrue(quad[0].toString().contains("1.50"));
        assertTrue(quad[0].toString().contains("ff00ff00"));
    }

    // Chooses the higher transparency level and maps cutoffs and packs bits.
    @Test
    void smallHelpers() {
        assertSame(SpriteTransparencyLevel.TRANSLUCENT, SpriteTransparencyLevel.OPAQUE.chooseNextLevel(SpriteTransparencyLevel.TRANSLUCENT));
        assertSame(SpriteTransparencyLevel.TRANSLUCENT, SpriteTransparencyLevel.TRANSLUCENT.chooseNextLevel(SpriteTransparencyLevel.OPAQUE));
        assertSame(SpriteTransparencyLevel.TRANSPARENT, SpriteTransparencyLevel.TRANSPARENT.chooseNextLevel(SpriteTransparencyLevel.TRANSPARENT));
        assertThrows(NullPointerException.class, () -> SpriteTransparencyLevel.OPAQUE.chooseNextLevel(null));
        assertSame(AlphaCutoffParameter.HALF, AlphaCutoffParameter.valueOf(0.5f));
        assertSame(AlphaCutoffParameter.ONE_TENTH, AlphaCutoffParameter.valueOf(0.1004f));
        assertThrows(IllegalArgumentException.class, () -> AlphaCutoffParameter.valueOf(0.3f));
        assertThrows(IllegalArgumentException.class, () -> AlphaCutoffParameter.valueOf(Float.NaN));
        assertEquals(0, MaterialParameters.pack(AlphaCutoffParameter.ZERO, false));
        assertEquals(1, MaterialParameters.pack(AlphaCutoffParameter.ZERO, true));
        assertEquals(7, MaterialParameters.pack(AlphaCutoffParameter.ONE, true));
        assertThrows(NullPointerException.class, () -> MaterialParameters.pack(null, true));
    }

    // Marks no sprites for empty lists and rejects nulls.
    @Test
    void spriteTicker() {
        java.util.List<Object> marked = new java.util.ArrayList<>();
        GenericSectionSpriteTicker<Object> ticker = new GenericSectionSpriteTicker<>(marked::add);
        assertThrows(NullPointerException.class, () -> new GenericSectionSpriteTicker<>(null));
        assertThrows(NullPointerException.class, () -> ticker.onRenderListUpdated(null));
        ticker.onRenderListUpdated(List.of());
        ticker.tickVisibleRenders();
        assertTrue(marked.isEmpty());
        assertEquals("A: 0", ticker.getDebugString());
    }

    // Ignores an empty quad on a builder without storage and fills the initial capacity exactly without growing.
    @Test
    void emptyQuadsAndExactFit() {
        ChunkMeshBufferBuilder builder = new ChunkMeshBufferBuilder(encoder(16, false), 16, 0, false);
        builder.start(0);
        builder.push(new ChunkVertexEncoder.Vertex[0], null);
        assertTrue(builder.isEmpty());
        builder.destroy();
        ChunkMeshBufferBuilder exact = new ChunkMeshBufferBuilder(encoder(16, false), 16, 64, false);
        exact.start(0);
        exact.push(ChunkVertexEncoder.Vertex.uninitializedQuad(), null);
        long first = org.lwjgl.system.MemoryUtil.memAddress(exact.slice());
        assertEquals(64, exact.slice().remaining());
        exact.start(0);
        exact.push(ChunkVertexEncoder.Vertex.uninitializedQuad(), null);
        assertEquals(first, org.lwjgl.system.MemoryUtil.memAddress(exact.slice()));
        exact.destroy();
    }
}
