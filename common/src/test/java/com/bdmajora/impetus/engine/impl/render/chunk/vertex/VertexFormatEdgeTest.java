package com.bdmajora.impetus.engine.impl.render.chunk.vertex;

import com.bdmajora.impetus.engine.impl.render.chunk.terrain.material.Material;
import com.bdmajora.impetus.engine.impl.render.chunk.vertex.format.ChunkVertexEncoder;
import com.bdmajora.impetus.engine.impl.render.chunk.vertex.format.impl.CompactChunkVertex;
import com.bdmajora.impetus.engine.impl.render.chunk.vertex.format.impl.MeshChunkVertex;
import com.bdmajora.impetus.engine.impl.render.chunk.vertex.format.impl.VanillaLikeChunkVertex;
import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;

// Tests that the vertex encoders write the documented layouts.
class VertexFormatEdgeTest {
    // Material with fixed bits.
    private static Material material(int bits) {
        Material material = Mockito.mock(Material.class);
        Mockito.when(material.bits()).thenReturn(bits);
        return material;
    }

    // Writes a vertex into native memory and returns the address range.
    private static long write(ChunkVertexEncoder encoder, ChunkVertexEncoder.Vertex vertex, int stride, int bits, int section) {
        long ptr = MemoryUtil.nmemAlloc(stride + 16);
        MemoryUtil.memSet(ptr, 0, stride + 16);
        long end = encoder.write(ptr, material(bits), vertex, section);
        assertEquals(ptr + stride, end);
        return ptr;
    }

    // Round trips positions through the compact layout and writes color and light.
    @Test
    void compactLayout() {
        CompactChunkVertex format = new CompactChunkVertex();
        ChunkVertexEncoder.Vertex v = new ChunkVertexEncoder.Vertex();
        v.x = 0f;
        v.y = 1.5f;
        v.z = 15.9f;
        v.color = 0x11223344;
        v.light = 0x00F000A0;
        v.rdhFactor = 0x01020304;
        long ptr = write(format.createEncoder(), v, CompactChunkVertex.STRIDE, 0x3, 0x1FF);
        try {
            int x = (MemoryUtil.memGetShort(ptr) & 0xFFFF) << 5 | (MemoryUtil.memGetShort(ptr + 6) & 31);
            assertEquals(0f, CompactChunkVertex.decodePosition(x), 0.001f);
            int z = (MemoryUtil.memGetShort(ptr + 4) & 0xFFFF) << 5 | ((MemoryUtil.memGetShort(ptr + 6) >> 10) & 31);
            assertEquals(15.9f, CompactChunkVertex.decodePosition(z), 0.001f);
            assertEquals(0x11223344, MemoryUtil.memGetInt(ptr + 8));
            assertEquals(0x01020304, MemoryUtil.memGetInt(ptr + 20));
            int light = MemoryUtil.memGetInt(ptr + 16) >>> 16;
            assertEquals(0xA0 | (0xF0 << 8), light);
            assertEquals(0xFF, (MemoryUtil.memGetInt(ptr + 16) >> 8) & 0xFF);
            assertEquals(0x3, MemoryUtil.memGetInt(ptr + 16) & 0xF);
        } finally {
            MemoryUtil.nmemFree(ptr);
        }
        assertTrue(format.getDefines().containsKey("USE_VERTEX_COMPRESSION"));
        assertEquals(-8f, format.getPositionOffset());
        assertEquals(CompactChunkVertex.STRIDE, format.getVertexFormat().getStride());
    }

    // Wraps out of range positions into the position mask without failing.
    @Test
    void compactExtremes() {
        ChunkVertexEncoder encoder = new CompactChunkVertex().createEncoder();
        for (float value : new float[]{Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, 1e30f, -1e30f, -8f, 24f}) {
            ChunkVertexEncoder.Vertex v = new ChunkVertexEncoder.Vertex();
            v.x = v.y = v.z = value;
            v.u = v.v = value;
            MemoryUtil.nmemFree(write(encoder, v, CompactChunkVertex.STRIDE, -1, -1));
        }
    }

    // Writes full precision floats and limits the texture coordinate below one.
    @Test
    void vanillaLikeLayout() {
        VanillaLikeChunkVertex format = new VanillaLikeChunkVertex();
        ChunkVertexEncoder.Vertex v = new ChunkVertexEncoder.Vertex();
        v.x = 1.25f;
        v.y = -3f;
        v.z = 100f;
        v.u = 5f;
        v.v = 0.5f;
        v.light = 0x00FF0010;
        long ptr = write(format.createEncoder(), v, VanillaLikeChunkVertex.STRIDE, 0x1FF, 0x1AB);
        try {
            assertEquals(1.25f, MemoryUtil.memGetFloat(ptr));
            assertEquals(-3f, MemoryUtil.memGetFloat(ptr + 4));
            assertEquals(100f, MemoryUtil.memGetFloat(ptr + 8));
            assertTrue(MemoryUtil.memGetFloat(ptr + 16) < 1f);
            assertEquals(0.5f, MemoryUtil.memGetFloat(ptr + 20));
            int params = MemoryUtil.memGetInt(ptr + 24);
            assertEquals(0xAB, (params >> 8) & 0xFF);
            assertEquals(0xFF, params & 0xFF);
            assertEquals(0x10 | (0xFF << 8), params >>> 16);
        } finally {
            MemoryUtil.nmemFree(ptr);
        }
        assertEquals(1f, format.getPositionScale());
        assertEquals(0f, format.getPositionOffset());
        assertEquals(1f, format.getTextureScale());
    }

    // Round trips mesh positions and clamps block coordinates to the section.
    @Test
    void meshPositions() {
        for (float value : new float[]{-7.9f, 0f, 7.5f, 15.99f, 23f}) {
            int packed = MeshChunkVertex.encodePosition(value);
            assertEquals(value, MeshChunkVertex.decodePosition(packed), 0.001f);
        }
        assertEquals(0, MeshChunkVertex.decodeBlockCoord(MeshChunkVertex.encodePosition(-3f)));
        assertEquals(15, MeshChunkVertex.decodeBlockCoord(MeshChunkVertex.encodePosition(20f)));
        assertEquals(7, MeshChunkVertex.decodeBlockCoord(MeshChunkVertex.encodePosition(7.5f)));
        ChunkVertexEncoder.Vertex v = new ChunkVertexEncoder.Vertex();
        v.x = 1f;
        v.y = 2f;
        v.z = 3f;
        v.color = 0x80FFFFFF;
        long ptr = write(MeshChunkVertex.INSTANCE.createEncoder(), v, MeshChunkVertex.STRIDE, 0, 0);
        try {
            assertEquals(MeshChunkVertex.encodePosition(1f), MeshChunkVertex.readPackedX(ptr));
            assertEquals(MeshChunkVertex.encodePosition(2f), MeshChunkVertex.readPackedY(ptr));
            assertEquals(MeshChunkVertex.encodePosition(3f), MeshChunkVertex.readPackedZ(ptr));
        } finally {
            MemoryUtil.nmemFree(ptr);
        }
        assertEquals(MeshChunkVertex.STRIDE, MeshChunkVertex.INSTANCE.getVertexFormat().getStride());
    }
}
