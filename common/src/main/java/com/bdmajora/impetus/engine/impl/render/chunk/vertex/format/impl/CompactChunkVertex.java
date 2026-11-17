package com.bdmajora.impetus.engine.impl.render.chunk.vertex.format.impl;

import com.bdmajora.impetus.engine.impl.gl.attribute.GlVertexAttributeFormat;
import com.bdmajora.impetus.engine.impl.gl.attribute.GlVertexFormat;
import com.bdmajora.impetus.engine.impl.render.chunk.terrain.material.Material;
import com.bdmajora.impetus.engine.impl.render.chunk.vertex.format.ChunkVertexEncoder;
import com.bdmajora.impetus.engine.impl.render.chunk.vertex.format.ChunkVertexType;

import java.util.Map;
import org.lwjgl.system.MemoryUtil;

// Terrain vertex of 24 bytes with quantized positions and texture coordinates.
public class CompactChunkVertex implements ChunkVertexType {
    // GL format of the vertex.
    public static final GlVertexFormat VERTEX_FORMAT = GlVertexFormat.builder(24)
            .addElement("a_PosId", 0, GlVertexAttributeFormat.UNSIGNED_SHORT, 4, false, true)
            .addElement("a_Color", 8, GlVertexAttributeFormat.UNSIGNED_BYTE, 4, true, false)
            .addElement("a_TexCoord", 12, GlVertexAttributeFormat.UNSIGNED_SHORT, 2, false, true)
            .addElement("a_LightCoord", 16, GlVertexAttributeFormat.UNSIGNED_INT, 1, false, true)
            .addElement("a_RdhFactor", 20, GlVertexAttributeFormat.BYTE, 4, true, false)
            .build();

    // Bytes of one vertex.
    public static final int STRIDE = 24;

    // Bits of a position.
    private static final int POSITION_BITS = 21;
    // Low bits of a position that are packed together.
    private static final int POSITION_LOW_BITS = 5;
    // Mask of a position.
    private static final int POSITION_MASK = (1 << POSITION_BITS) - 1;
    // Mask of the low bits of a position.
    private static final int POSITION_LOW_MASK = (1 << POSITION_LOW_BITS) - 1;
    // Number of position values.
    private static final int POSITION_MAX_VALUE = 1 << POSITION_BITS;

    // Bits of a texture coordinate.
    private static final int TEXTURE_BITS = 18;
    // Low bits of a texture coordinate that are packed with the material.
    private static final int TEXTURE_LOW_BITS = 2;
    // Mask of a texture coordinate.
    private static final int TEXTURE_MASK = (1 << TEXTURE_BITS) - 1;
    // Mask of the low bits of a texture coordinate.
    private static final int TEXTURE_LOW_MASK = (1 << TEXTURE_LOW_BITS) - 1;
    // Value of the texture coordinate one.
    private static final int TEXTURE_MAX_VALUE = 1 << (TEXTURE_BITS - 1);

    // Bits of the material.
    private static final int MATERIAL_BITS = 4;
    // Mask of the material.
    private static final int MATERIAL_MASK = (1 << MATERIAL_BITS) - 1;
    // Shift of the low bits of u.
    private static final int TEXTURE_LOW_U_SHIFT = MATERIAL_BITS;
    // Shift of the low bits of v.
    private static final int TEXTURE_LOW_V_SHIFT = MATERIAL_BITS + TEXTURE_LOW_BITS;

    // Offset that makes positions unsigned.
    private static final float MODEL_ORIGIN = 8.0f;
    // Range of positions in blocks.
    private static final float MODEL_RANGE = 32.0f;
    // Blocks per position step.
    private static final float MODEL_SCALE = MODEL_RANGE / POSITION_MAX_VALUE;
    // Position steps per block.
    private static final float MODEL_SCALE_INV = POSITION_MAX_VALUE / MODEL_RANGE;

    // Texture coordinate per step.
    private static final float TEXTURE_SCALE = (1.0f / TEXTURE_MAX_VALUE);

    // Returns the texture scale.
    @Override
    public float getTextureScale() {
        return TEXTURE_SCALE;
    }

    // Returns the position scale.
    @Override
    public float getPositionScale() {
        return MODEL_SCALE;
    }

    // Returns the position offset.
    @Override
    public float getPositionOffset() {
        return -MODEL_ORIGIN;
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
            int x = encodePosition(vertex.x);
            int y = encodePosition(vertex.y);
            int z = encodePosition(vertex.z);

            MemoryUtil.memPutShort(ptr + 0, (short) (x >>> POSITION_LOW_BITS));
            MemoryUtil.memPutShort(ptr + 2, (short) (y >>> POSITION_LOW_BITS));
            MemoryUtil.memPutShort(ptr + 4, (short) (z >>> POSITION_LOW_BITS));
            MemoryUtil.memPutShort(ptr + 6, (short) ((x & POSITION_LOW_MASK)
                    | ((y & POSITION_LOW_MASK) << POSITION_LOW_BITS)
                    | ((z & POSITION_LOW_MASK) << (POSITION_LOW_BITS * 2))));

            MemoryUtil.memPutInt(ptr + 8, vertex.color);

            int u = encodeTexture(vertex.u);
            int v = encodeTexture(vertex.v);

            MemoryUtil.memPutShort(ptr + 12, (short) (u >>> TEXTURE_LOW_BITS));
            MemoryUtil.memPutShort(ptr + 14, (short) (v >>> TEXTURE_LOW_BITS));

            MemoryUtil.memPutInt(ptr + 16, (encodeDrawParameters(material, sectionIndex, u, v) << 0) | (encodeLight(vertex.light) << 16));
            MemoryUtil.memPutInt(ptr + 20, vertex.rdhFactor);

            return ptr + STRIDE;
        };
    }

    // Returns the shader definitions including the compression flag.
    @Override
    public Map<String, String> getDefines() {
        var map = ChunkVertexType.super.getDefines();
        map.put("USE_VERTEX_COMPRESSION", "");
        return map;
    }

    // Returns the fixed point position of a coordinate.
    private static int encodePosition(float value) {
        return (int) ((MODEL_ORIGIN + value) * MODEL_SCALE_INV) & POSITION_MASK;
    }

    // Returns the coordinate of a fixed point position.
    public static float decodePosition(int value) {
        return (((float) (value & POSITION_MASK)) / MODEL_SCALE_INV) - MODEL_ORIGIN;
    }

    // Returns the section index and texture bits and material bits.
    private static int encodeDrawParameters(Material material, int sectionIndex, int u, int v) {
        return (((sectionIndex & 0xFF) << 8)
                | ((v & TEXTURE_LOW_MASK) << TEXTURE_LOW_V_SHIFT)
                | ((u & TEXTURE_LOW_MASK) << TEXTURE_LOW_U_SHIFT)
                | ((material.bits() & MATERIAL_MASK) << 0));
    }

    // Returns the packed light of block and sky.
    private static int encodeLight(int light) {
        int block = light & 0xFF;
        int sky = (light >> 16) & 0xFF;
        return ((block << 0) | (sky << 8));
    }

    // Returns the fixed point texture coordinate.
    private static int encodeTexture(float value) {
        return Math.round(value * TEXTURE_MAX_VALUE) & TEXTURE_MASK;
    }
}
