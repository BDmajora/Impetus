package com.bdmajora.impetus.engine.impl.render.chunk.vertex.format.impl;

import com.bdmajora.impetus.engine.impl.gl.attribute.GlVertexAttributeFormat;
import com.bdmajora.impetus.engine.impl.gl.attribute.GlVertexFormat;
import com.bdmajora.impetus.engine.impl.render.chunk.terrain.material.Material;
import com.bdmajora.impetus.engine.impl.render.chunk.vertex.format.ChunkVertexEncoder;
import com.bdmajora.impetus.engine.impl.render.chunk.vertex.format.ChunkVertexType;
import org.lwjgl.system.MemoryUtil;

// Terrain vertex of 32 bytes with full precision floats.
public class VanillaLikeChunkVertex implements ChunkVertexType {
    // Bytes of one vertex.
    public static final int STRIDE = 32;

    // GL format of the vertex.
    public static final GlVertexFormat VERTEX_FORMAT = baseFormat(STRIDE)
            .addElement("a_RdhFactor", 28, GlVertexAttributeFormat.BYTE, 4, true, false)
            .build();

    // Returns a builder with the four base attributes.
    public static GlVertexFormat.Builder baseFormat(int stride) {
        return GlVertexFormat.builder(stride)
                .addElement("a_PosId", 0, GlVertexAttributeFormat.FLOAT, 3, false, false)
                .addElement("a_Color", 12, GlVertexAttributeFormat.UNSIGNED_BYTE, 4, true, false)
                .addElement("a_TexCoord", 16, GlVertexAttributeFormat.FLOAT, 2, false, false)
                .addElement("a_LightCoord", 24, GlVertexAttributeFormat.UNSIGNED_INT, 1, false, true);
    }

    // Returns the position scale.
    @Override
    public float getPositionScale() {
        return 1f;
    }

    // Returns the position offset.
    @Override
    public float getPositionOffset() {
        return 0;
    }

    // Returns the texture scale.
    @Override
    public float getTextureScale() {
        return 1f;
    }

    // Returns the GL format.
    @Override
    public GlVertexFormat getVertexFormat() {
        return VERTEX_FORMAT;
    }

    // Creates an encoder that writes one vertex per call.
    @Override
    public ChunkVertexEncoder createEncoder() {
        return (ptr, material, vertex, sectionIndex) -> {
            writeBase(ptr, material, vertex, sectionIndex);
            MemoryUtil.memPutInt(ptr + 28, vertex.rdhFactor);

            return ptr + STRIDE;
        };
    }

    // Writes the first 28 bytes of a vertex.
    public static void writeBase(long ptr, Material material, ChunkVertexEncoder.Vertex vertex, int sectionIndex) {
        MemoryUtil.memPutFloat(ptr + 0, vertex.x);
        MemoryUtil.memPutFloat(ptr + 4, vertex.y);
        MemoryUtil.memPutFloat(ptr + 8, vertex.z);
        MemoryUtil.memPutInt(ptr + 12, vertex.color);
        MemoryUtil.memPutFloat(ptr + 16, encodeTexture(vertex.u));
        MemoryUtil.memPutFloat(ptr + 20, encodeTexture(vertex.v));
        MemoryUtil.memPutInt(ptr + 24, encodeDrawParameters(material, sectionIndex) | (encodeLight(vertex.light) << 16));
    }

    // Returns the section index and material bits.
    private static int encodeDrawParameters(Material material, int sectionIndex) {
        return ((sectionIndex & 0xFF) << 8) | (material.bits() & 0xFF);
    }

    // Returns the packed light of block and sky.
    private static int encodeLight(int light) {
        int block = light & 0xFF;
        int sky = (light >> 16) & 0xFF;
        return block | (sky << 8);
    }

    // Returns the texture coordinate limited below one.
    private static float encodeTexture(float value) {
        return Math.min(0.99999997F, value);
    }
}
