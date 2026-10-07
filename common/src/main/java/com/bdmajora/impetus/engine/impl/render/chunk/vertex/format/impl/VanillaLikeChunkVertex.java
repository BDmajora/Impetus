package com.bdmajora.impetus.engine.impl.render.chunk.vertex.format.impl;

import com.bdmajora.impetus.engine.impl.gl.attribute.GlVertexAttributeFormat;
import com.bdmajora.impetus.engine.impl.gl.attribute.GlVertexFormat;
import com.bdmajora.impetus.engine.impl.render.chunk.terrain.material.Material;
import com.bdmajora.impetus.engine.impl.render.chunk.vertex.format.ChunkVertexEncoder;
import com.bdmajora.impetus.engine.impl.render.chunk.vertex.format.ChunkVertexType;
import org.lwjgl.system.MemoryUtil;

// Wide full-precision terrain vertex for modded models with fine sub-block offsets, where CompactChunkVertex's quantised positions/UVs become visible
public class VanillaLikeChunkVertex implements ChunkVertexType {
    public static final int STRIDE = 32;

    // The base layout plus the bilinear AO correction factor the built-in terrain shader reads
    public static final GlVertexFormat VERTEX_FORMAT = baseFormat(STRIDE)
            .addElement("a_RdhFactor", 28, GlVertexAttributeFormat.BYTE, 4, true, false)
            .build();

    // The four base attributes at their fixed offsets, for a wider format that appends its own after byte 28
    public static GlVertexFormat.Builder baseFormat(int stride) {
        return GlVertexFormat.builder(stride)
                .addElement("a_PosId", 0, GlVertexAttributeFormat.FLOAT, 3, false, false)
                .addElement("a_Color", 12, GlVertexAttributeFormat.UNSIGNED_BYTE, 4, true, false)
                .addElement("a_TexCoord", 16, GlVertexAttributeFormat.FLOAT, 2, false, false)
                .addElement("a_LightCoord", 24, GlVertexAttributeFormat.UNSIGNED_INT, 1, false, true);
    }

    // Positions are stored as float
    @Override
    public float getPositionScale() {
        return 1f;
    }

    // No offset
    @Override
    public float getPositionOffset() {
        return 0;
    }

    // UVs are stored as float
    @Override
    public float getTextureScale() {
        return 1f;
    }

    // Vanilla-like layout the shader pack transform expects
    @Override
    public GlVertexFormat getVertexFormat() {
        return VERTEX_FORMAT;
    }

    // Writes one vertex per call
    @Override
    public ChunkVertexEncoder createEncoder() {
        return (ptr, material, vertex, sectionIndex) -> {
            writeBase(ptr, material, vertex, sectionIndex);
            MemoryUtil.memPutInt(ptr + 28, vertex.rdhFactor);

            return ptr + STRIDE;
        };
    }

    // The 28-byte layout itself, shared with the shader-pack format that extends it
    public static void writeBase(long ptr, Material material, ChunkVertexEncoder.Vertex vertex, int sectionIndex) {
        MemoryUtil.memPutFloat(ptr + 0, vertex.x);
        MemoryUtil.memPutFloat(ptr + 4, vertex.y);
        MemoryUtil.memPutFloat(ptr + 8, vertex.z);
        MemoryUtil.memPutInt(ptr + 12, vertex.color);
        MemoryUtil.memPutFloat(ptr + 16, encodeTexture(vertex.u));
        MemoryUtil.memPutFloat(ptr + 20, encodeTexture(vertex.v));
        MemoryUtil.memPutInt(ptr + 24, encodeDrawParameters(material, sectionIndex) | (encodeLight(vertex.light) << 16));
    }

    // Material bits and section index into one int
    private static int encodeDrawParameters(Material material, int sectionIndex) {
        return ((sectionIndex & 0xFF) << 8) | (material.bits() & 0xFF);
    }

    // Packed light into the shader's lightmap coordinates
    private static int encodeLight(int light) {
        int block = light & 0xFF;
        int sky = (light >> 16) & 0xFF;
        return block | (sky << 8);
    }

    // UVs pass through
    private static float encodeTexture(float value) {
        return Math.min(0.99999997F, value);
    }
}
