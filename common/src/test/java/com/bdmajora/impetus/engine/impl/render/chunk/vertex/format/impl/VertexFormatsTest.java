package com.bdmajora.impetus.engine.impl.render.chunk.vertex.format.impl;

import com.bdmajora.impetus.engine.api.util.ColorABGR;
import com.bdmajora.impetus.engine.impl.render.chunk.vertex.format.ChunkMeshFormats;
import com.bdmajora.impetus.engine.impl.render.chunk.vertex.format.ChunkVertexEncoder;
import com.bdmajora.impetus.engine.impl.render.chunk.vertex.format.ChunkVertexType;
import com.bdmajora.testing.Passes;
import com.bdmajora.testing.TestGl;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class VertexFormatsTest {
    private static ChunkVertexEncoder.Vertex vertex() {
        ChunkVertexEncoder.Vertex v = ChunkVertexEncoder.Vertex.uninitializedQuad()[0];
        v.x = 1.5f;
        v.y = 2.25f;
        v.z = -0.5f;
        v.color = ColorABGR.pack(200, 100, 50, 128);
        v.u = 0.25f;
        v.v = 0.75f;
        v.light = 0x00F000A0;
        return v;
    }

    @Test
    void compactFormatQuantisesPositionsAndUvs() {
        ChunkVertexType type = ChunkMeshFormats.COMPACT;
        assertEquals(20, type.getVertexFormat().getStride());
        assertEquals(-8f, type.getPositionOffset());
        assertTrue(type.getPositionScale() > 0);
        Map<String, String> defines = type.getDefines();
        assertTrue(defines.containsKey("USE_VERTEX_COMPRESSION"));
        assertEquals(String.valueOf(type.getTextureScale()), defines.get("VERT_TEX_SCALE"));
        long ptr = TestGl.gl().nmemCalloc(1, 64);
        long next = type.createEncoder().write(ptr, Passes.CUTOUT_MATERIAL, vertex(), 7);
        assertEquals(ptr + CompactChunkVertex.STRIDE, next);
        assertEquals(1.5f, CompactChunkVertex.decodePosition(TestGl.gl().memGetShort(ptr)), 1e-3);
        assertEquals(2.25f, CompactChunkVertex.decodePosition(TestGl.gl().memGetShort(ptr + 2)), 1e-3);
        assertEquals(-0.5f, CompactChunkVertex.decodePosition(TestGl.gl().memGetShort(ptr + 4)), 1e-3);
        assertEquals(Passes.CUTOUT_MATERIAL.bits(), TestGl.gl().memGetByte(ptr + 6));
        assertEquals(7, TestGl.gl().memGetByte(ptr + 7));
        assertEquals(0x00F000A0, TestGl.gl().memGetInt(ptr + 16));
        assertEquals(8192, TestGl.gl().memGetShort(ptr + 12));
        TestGl.gl().nmemFree(ptr);
    }

    @Test
    void vanillaLikeFormatKeepsFullPrecision() {
        ChunkVertexType type = ChunkMeshFormats.VANILLA_LIKE;
        assertEquals(28, type.getVertexFormat().getStride());
        assertEquals(1f, type.getPositionScale());
        assertEquals(0f, type.getPositionOffset());
        assertEquals(1f, type.getTextureScale());
        assertFalse(type.getDefines().containsKey("USE_VERTEX_COMPRESSION"));
        long ptr = TestGl.gl().nmemCalloc(1, 64);
        assertEquals(ptr + VanillaLikeChunkVertex.STRIDE, type.createEncoder().write(ptr, Passes.CUTOUT_MATERIAL, vertex(), 3));
        assertEquals(1.5f, TestGl.gl().memGetFloat(ptr));
        assertEquals(0.75f, TestGl.gl().memGetFloat(ptr + 20));
        int params = TestGl.gl().memGetInt(ptr + 24);
        assertEquals(Passes.CUTOUT_MATERIAL.bits(), params & 0xFF);
        assertEquals(3, (params >> 8) & 0xFF);
        assertEquals(0xA0 | (0xF0 << 8), params >>> 16);
        assertEquals(32, VanillaLikeChunkVertex.baseFormat(32).addElement("extra", 28, com.bdmajora.impetus.engine.impl.gl.attribute.GlVertexAttributeFormat.FLOAT, 1, false, false).build().getStride());
        TestGl.gl().nmemFree(ptr);
    }

    @Test
    void meshFormatPacksEverythingIntoSixteenBytes() {
        MeshChunkVertex type = MeshChunkVertex.INSTANCE;
        assertEquals(16, type.getVertexFormat().getStride());
        assertEquals(-8f, type.getPositionOffset());
        assertTrue(type.getPositionScale() > 0);
        assertTrue(type.getTextureScale() > 0);
        assertEquals("32768", type.getDefines().get("TEXTURE_MAX_SCALE"));
        long ptr = TestGl.gl().nmemCalloc(1, 64);
        assertEquals(ptr + MeshChunkVertex.STRIDE, type.createEncoder().write(ptr, Passes.CUTOUT_MATERIAL, vertex(), 0));
        assertEquals(1.5f, MeshChunkVertex.decodePosition(MeshChunkVertex.readPackedX(ptr)), 1e-3);
        assertEquals(2.25f, MeshChunkVertex.decodePosition(MeshChunkVertex.readPackedY(ptr)), 1e-3);
        assertEquals(-0.5f, MeshChunkVertex.decodePosition(MeshChunkVertex.readPackedZ(ptr)), 1e-3);
        assertEquals(1, MeshChunkVertex.decodeBlockCoord(MeshChunkVertex.readPackedX(ptr)));
        assertEquals(0, MeshChunkVertex.decodeBlockCoord(MeshChunkVertex.readPackedZ(ptr)));
        assertEquals(15, MeshChunkVertex.decodeBlockCoord(65535));
        int second = TestGl.gl().memGetInt(ptr + 4);
        assertEquals(Passes.CUTOUT_MATERIAL.bits(), (second >> 16) & 0xFF);
        assertEquals(0xA0, second >>> 24);
        int colour = TestGl.gl().memGetInt(ptr + 8);
        assertEquals(0xF0, colour >>> 24);
        assertEquals(200 * 128 / 255, ColorABGR.unpackRed(colour));
        TestGl.gl().nmemFree(ptr);
        assertTrue(vertex().toString().startsWith("XYZ:"));
        new ChunkMeshFormats();
    }
}
