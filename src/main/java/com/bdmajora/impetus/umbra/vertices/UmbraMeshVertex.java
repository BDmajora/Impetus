package com.bdmajora.impetus.umbra.vertices;

import com.bdmajora.impetus.engine.impl.gl.attribute.GlVertexAttributeFormat;
import com.bdmajora.impetus.engine.impl.gl.attribute.GlVertexFormat;
import com.bdmajora.impetus.engine.impl.render.chunk.vertex.format.ChunkVertexEncoder;
import com.bdmajora.impetus.engine.impl.render.chunk.vertex.format.impl.MeshChunkVertex;
import com.bdmajora.impetus.engine.impl.render.chunk.vertex.format.impl.MeshVertexLayout;
import org.lwjgl.system.MemoryUtil;

// The terrain vertex while a shader pack draws through the mesh backend: every attribute UmbraChunkVertexType carries, in 32 bytes the mesh shader fetches as two uvec4s, so a quad is one 128-byte cache line; mc_midTexCoord is not stored at all, the mesh shader averages the quad's four UVs itself
public class UmbraMeshVertex implements MeshVertexLayout {
    public static final UmbraMeshVertex INSTANCE = new UmbraMeshVertex();

    public static final int STRIDE = 32;

    // UVs as 16-bit fractions of the atlas
    public static final int TEXTURE_SCALE = 65536;

    // Two uvec4s; the format only exists because ChunkVertexType demands one, nothing binds it as attributes
    public static final GlVertexFormat VERTEX_FORMAT = GlVertexFormat.builder(STRIDE)
            .addElement("a_PackedA", 0, GlVertexAttributeFormat.UNSIGNED_INT, 4, false, true)
            .addElement("a_PackedB", 16, GlVertexAttributeFormat.UNSIGNED_INT, 4, false, true)
            .build();

    private UmbraMeshVertex() {
    }

    // Positions share MeshChunkVertex's 16-bit encoding
    @Override
    public float getPositionScale() {
        return MeshChunkVertex.INSTANCE.getPositionScale();
    }

    // The same small negative margin
    @Override
    public float getPositionOffset() {
        return MeshChunkVertex.INSTANCE.getPositionOffset();
    }

    // UVs are 16-bit fractions
    @Override
    public float getTextureScale() {
        return 1.0f / TEXTURE_SCALE;
    }

    // The two-uvec4 layout
    @Override
    public GlVertexFormat getVertexFormat() {
        return VERTEX_FORMAT;
    }

    // Words in mesh-shader order: position and material with block light, colour, UV, then normal with sky light, tangent, mc_Entity and at_midBlock
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

    // A UV in 0..1 to 16 bits, the top value standing in for 1.0
    static int encodeTexture(float value) {
        return Math.clamp(Math.round(value * TEXTURE_SCALE), 0, TEXTURE_SCALE - 1);
    }

    // at_midBlock's rounding and range, as UmbraChunkVertexType writes it
    private static int signedByte(float value) {
        return clampByte(Math.round(value));
    }

    // -128..127
    private static int clampByte(int value) {
        return Math.clamp(value, Byte.MIN_VALUE, Byte.MAX_VALUE);
    }

    // Short range
    private static int clampShort(int value) {
        return Math.clamp(value, Short.MIN_VALUE, Short.MAX_VALUE);
    }
}
