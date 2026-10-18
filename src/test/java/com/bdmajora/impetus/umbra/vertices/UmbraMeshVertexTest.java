package com.bdmajora.impetus.umbra.vertices;

import com.bdmajora.impetus.engine.api.util.NormI8;
import com.bdmajora.impetus.engine.impl.render.chunk.terrain.material.Material;
import com.bdmajora.impetus.engine.impl.render.chunk.vertex.format.ChunkVertexEncoder;
import com.bdmajora.impetus.engine.impl.render.chunk.vertex.format.impl.MeshChunkVertex;
import com.bdmajora.impetus.engine.impl.render.chunk.vertex.format.impl.MeshVertexLayout;
import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UmbraMeshVertexTest {
    @Test
    void everyAttributeFitsTwoWordsOfFour() {
        UmbraMeshVertex type = UmbraMeshVertex.INSTANCE;
        assertInstanceOf(MeshVertexLayout.class, type);
        assertEquals(32, type.getVertexFormat().getStride());
        assertEquals(MeshChunkVertex.INSTANCE.getPositionScale(), type.getPositionScale());
        assertEquals(MeshChunkVertex.INSTANCE.getPositionOffset(), type.getPositionOffset());
        assertEquals(1.0f / 65536, type.getTextureScale());

        ChunkVertexEncoder.Vertex vertex = new ChunkVertexEncoder.Vertex();
        vertex.x = 1.5f;
        vertex.y = -0.25f;
        vertex.z = 15.0f;
        vertex.color = 0x80FF4020;
        vertex.u = 0.5f;
        vertex.v = 1.0f;
        vertex.light = 240 | (176 << 16);
        vertex.trueNormal = NormI8.pack(0f, 1f, 0f);
        vertex.tangent = NormI8.pack(1f, 0f, 0f, -1f);
        vertex.blockId = 40_000;
        vertex.blockRenderType = -1;
        vertex.blockData = 7;
        vertex.midBlockX = 0.5f;
        vertex.midBlockY = -3f;
        vertex.midBlockZ = 0f;
        vertex.blockEmission = 14;
        Material material = mock(Material.class);
        when(material.bits()).thenReturn(0b101);

        ByteBuffer memory = ByteBuffer.allocateDirect(64);
        long base = MemoryUtil.memAddress(memory);
        assertEquals(base + 32, type.createEncoder().write(base, material, vertex, 9));

        // Positions in MeshChunkVertex's own spots, so the packer and section bounds read them unchanged
        assertEquals(1.5f, MeshChunkVertex.decodePosition(MeshChunkVertex.readPackedX(base)), 1.0e-3f);
        assertEquals(-0.25f, MeshChunkVertex.decodePosition(MeshChunkVertex.readPackedY(base)), 1.0e-3f);
        assertEquals(15.0f, MeshChunkVertex.decodePosition(MeshChunkVertex.readPackedZ(base)), 1.0e-3f);
        // Material bits then block light above z
        assertEquals(0b101, MemoryUtil.memGetByte(base + 6));
        assertEquals((byte) 240, MemoryUtil.memGetByte(base + 7));
        assertEquals(0x80FF4020, MemoryUtil.memGetInt(base + 8));
        // UV as 16-bit fractions, 1.0 held at the top value
        assertEquals(0x8000 | (0xFFFF << 16), MemoryUtil.memGetInt(base + 12));
        // Sky light rides in the normal's padding byte
        assertEquals(NormI8.pack(0f, 1f, 0f) | (176 << 24), MemoryUtil.memGetInt(base + 16));
        assertEquals(NormI8.pack(1f, 0f, 0f, -1f), MemoryUtil.memGetInt(base + 20));
        // mc_Entity: the id clamped to a short, render type and metadata as bytes
        assertEquals(Short.MAX_VALUE, MemoryUtil.memGetShort(base + 24));
        assertEquals((byte) -1, MemoryUtil.memGetByte(base + 26));
        assertEquals(7, MemoryUtil.memGetByte(base + 27));
        // at_midBlock times 64 as signed bytes, emission on top
        assertEquals(32, MemoryUtil.memGetByte(base + 28));
        assertEquals(-128, MemoryUtil.memGetByte(base + 29));
        assertEquals(0, MemoryUtil.memGetByte(base + 30));
        assertEquals(14, MemoryUtil.memGetByte(base + 31));
    }

    @Test
    void texturesClampIntoSixteenBits() {
        assertEquals(0, UmbraMeshVertex.encodeTexture(-0.5f));
        assertEquals(65535, UmbraMeshVertex.encodeTexture(2.0f));
        assertEquals(16384, UmbraMeshVertex.encodeTexture(0.25f));
    }
}
