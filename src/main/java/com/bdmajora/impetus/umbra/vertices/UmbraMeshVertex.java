package com.bdmajora.impetus.umbra.vertices;

import com.bdmajora.impetus.engine.impl.gl.attribute.GlVertexAttributeFormat;
import com.bdmajora.impetus.engine.impl.gl.attribute.GlVertexFormat;
import com.bdmajora.impetus.engine.impl.render.chunk.vertex.format.ChunkVertexEncoder;
import com.bdmajora.impetus.engine.impl.render.chunk.vertex.format.impl.MeshChunkVertex;
import com.bdmajora.impetus.engine.impl.render.chunk.vertex.format.impl.MeshVertexLayout;
import org.lwjgl.system.MemoryUtil;

// Mesh shader vertex layout of pack terrain.
public class UmbraMeshVertex implements MeshVertexLayout {
    // Shared instance.
    public static final UmbraMeshVertex INSTANCE = new UmbraMeshVertex();

    // Bytes of one vertex.
    public static final int STRIDE = 32;

    // Scale of texture coordinates.
    public static final int TEXTURE_SCALE = 65536;

    // Vertex format.
    public static final GlVertexFormat VERTEX_FORMAT = GlVertexFormat.builder(STRIDE)
            .addElement("a_PackedA", 0, GlVertexAttributeFormat.UNSIGNED_INT, 4, false, true)
            .addElement("a_PackedB", 16, GlVertexAttributeFormat.UNSIGNED_INT, 4, false, true)
            .build();

    // Prevents instantiation
    private UmbraMeshVertex() {
    }

    // Returns the position scale.
    @Override
    public float getPositionScale() {
        return MeshChunkVertex.INSTANCE.getPositionScale();
    }

    // Returns the position offset.
    @Override
    public float getPositionOffset() {
        return MeshChunkVertex.INSTANCE.getPositionOffset();
    }

    // Returns the texture scale.
    @Override
    public float getTextureScale() {
        return 1.0f / TEXTURE_SCALE;
    }

    // Returns the vertex format.
    @Override
    public GlVertexFormat getVertexFormat() {
        return VERTEX_FORMAT;
    }

    // Returns a vertex encoder.
    @Override
    public ChunkVertexEncoder createEncoder() {
        return (ptr, material, vertex, sectionIndex) -> {
            int blockLight = vertex.light & 0xFF;
            int skyLight = (vertex.light >> 16) & 0xFF;

            MemoryUtil.memPutInt(ptr, MeshChunkVertex.encodePosition(vertex.x) | (MeshChunkVertex.encodePosition(vertex.y) << 16));
            MemoryUtil.memPutInt(ptr + 4, MeshChunkVertex.encodePosition(vertex.z) | ((material.bits() & 0xFF) << 16) | (blockLight << 24));
            MemoryUtil.memPutInt(ptr + 8, vertex.color);
            MemoryUtil.memPutInt(ptr + 12, encodeTexture(vertex.u) | (encodeTexture(vertex.v) << 16));
            MemoryUtil.memPutInt(ptr + 16, (vertex.trueNormal & 0x00FFFFFF) | (skyLight << 24));
            MemoryUtil.memPutInt(ptr + 20, vertex.tangent);
            MemoryUtil.memPutInt(ptr + 24, (clampShort(vertex.blockId) & 0xFFFF) | ((clampByte(vertex.blockRenderType) & 0xFF) << 16)
                    | ((vertex.blockData & 0xFF) << 24));
            MemoryUtil.memPutInt(ptr + 28, (signedByte(vertex.midBlockX * 64.0f) & 0xFF) | ((signedByte(vertex.midBlockY * 64.0f) & 0xFF) << 8)
                    | ((signedByte(vertex.midBlockZ * 64.0f) & 0xFF) << 16) | ((Math.clamp(vertex.blockEmission, 0, 255) & 0xFF) << 24));

            return ptr + STRIDE;
        };
    }

    // Returns a texture coordinate in fixed point.
    static int encodeTexture(float value) {
        return Math.clamp(Math.round(value * TEXTURE_SCALE), 0, TEXTURE_SCALE - 1);
    }

    // Returns a unit value as a signed byte.
    private static int signedByte(float value) {
        return clampByte(Math.round(value));
    }

    // Limits the byte.
    private static int clampByte(int value) {
        return Math.clamp(value, Byte.MIN_VALUE, Byte.MAX_VALUE);
    }

    // Limits the short.
    private static int clampShort(int value) {
        return Math.clamp(value, Short.MIN_VALUE, Short.MAX_VALUE);
    }
}
