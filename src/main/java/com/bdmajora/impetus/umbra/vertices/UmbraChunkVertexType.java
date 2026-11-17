package com.bdmajora.impetus.umbra.vertices;

import com.bdmajora.impetus.engine.impl.gl.attribute.GlVertexAttributeFormat;
import com.bdmajora.impetus.engine.impl.gl.attribute.GlVertexFormat;
import com.bdmajora.impetus.engine.impl.render.chunk.vertex.format.ChunkVertexEncoder;
import com.bdmajora.impetus.engine.impl.render.chunk.vertex.format.ChunkVertexType;
import org.lwjgl.system.MemoryUtil;

import java.util.Map;

// Chunk vertex of pack terrain on the OpenGL path in 36 bytes.
public class UmbraChunkVertexType implements ChunkVertexType {
    // Shared instance.
    public static final UmbraChunkVertexType INSTANCE = new UmbraChunkVertexType();

    // Bytes of one vertex.
    public static final int STRIDE = 36;

    // Define that switches the default chunk shader to this layout's decode.
    public static final String LAYOUT_DEFINE = "USE_PACKED_EXTENDED_VERTEX";

    // Position steps over the range per axis.
    private static final int POSITION_MAX_VALUE = 65536;
    // Offset that makes section-local positions unsigned.
    private static final float MODEL_ORIGIN = 8.0f;
    // Range of section-local positions in blocks.
    private static final float MODEL_RANGE = 32.0f;
    // Blocks per position step.
    private static final float MODEL_SCALE = MODEL_RANGE / POSITION_MAX_VALUE;
    // Position steps per block.
    private static final float MODEL_SCALE_INV = POSITION_MAX_VALUE / MODEL_RANGE;
    // Texture coordinate steps per unit.
    private static final int TEXTURE_SCALE = UmbraMeshVertex.TEXTURE_SCALE;

    // Byte offsets of the nine words.
    private static final int OFFSET_XY = 0;
    private static final int OFFSET_Z_META = 4;
    private static final int OFFSET_COLOR = 8;
    private static final int OFFSET_UV = 12;
    private static final int OFFSET_MID_UV = 16;
    private static final int OFFSET_NORMAL = 20;
    private static final int OFFSET_TANGENT = 24;
    private static final int OFFSET_BLOCK = 28;
    private static final int OFFSET_MID_BLOCK = 32;

    // Vertex format of three packed words.
    public static final GlVertexFormat VERTEX_FORMAT = GlVertexFormat.builder(STRIDE)
            .addElement("a_PackedA", OFFSET_XY, GlVertexAttributeFormat.UNSIGNED_INT, 4, false, true)
            .addElement("a_PackedB", OFFSET_MID_UV, GlVertexAttributeFormat.UNSIGNED_INT, 4, false, true)
            .addElement("a_PackedC", OFFSET_MID_BLOCK, GlVertexAttributeFormat.UNSIGNED_INT, 1, false, true)
            .build();

    // Prevents instantiation
    private UmbraChunkVertexType() {
    }

    // Returns the blocks per position step.
    @Override
    public float getPositionScale() {
        return MODEL_SCALE;
    }

    // Returns the position offset.
    @Override
    public float getPositionOffset() {
        return -MODEL_ORIGIN;
    }

    // Returns the texture coordinate per step.
    @Override
    public float getTextureScale() {
        return 1.0f / TEXTURE_SCALE;
    }

    // Returns the vertex format.
    @Override
    public GlVertexFormat getVertexFormat() {
        return VERTEX_FORMAT;
    }

    // Returns the scale defines plus the switch to this layout's decode.
    @Override
    public Map<String, String> getDefines() {
        Map<String, String> defines = ChunkVertexType.super.getDefines();
        defines.put(LAYOUT_DEFINE, "");
        return defines;
    }

    // Returns a vertex encoder.
    @Override
    public ChunkVertexEncoder createEncoder() {
        return (ptr, material, vertex, sectionIndex) -> {
            int blockLight = vertex.light & 0xFF;
            int skyLight = (vertex.light >> 16) & 0xFF;

            MemoryUtil.memPutInt(ptr + OFFSET_XY, encodePosition(vertex.x) | (encodePosition(vertex.y) << 16));
            // Packs the material and emission into the byte after the draw id.
            MemoryUtil.memPutInt(ptr + OFFSET_Z_META, encodePosition(vertex.z) | ((sectionIndex & 0xFF) << 16)
                    | ((material.bits() & 0xF) << 24) | (Math.clamp(vertex.blockEmission, 0, 15) << 28));
            MemoryUtil.memPutInt(ptr + OFFSET_COLOR, vertex.color);
            MemoryUtil.memPutInt(ptr + OFFSET_UV, encodeTexture(vertex.u) | (encodeTexture(vertex.v) << 16));
            MemoryUtil.memPutInt(ptr + OFFSET_MID_UV, encodeTexture(vertex.midTexU) | (encodeTexture(vertex.midTexV) << 16));
            MemoryUtil.memPutInt(ptr + OFFSET_NORMAL, (vertex.trueNormal & 0x00FFFFFF) | (blockLight << 24));
            MemoryUtil.memPutInt(ptr + OFFSET_TANGENT, vertex.tangent);
            MemoryUtil.memPutInt(ptr + OFFSET_BLOCK, (Math.clamp(vertex.blockId, Short.MIN_VALUE, Short.MAX_VALUE) & 0xFFFF)
                    | ((Math.clamp(vertex.blockRenderType, Byte.MIN_VALUE, Byte.MAX_VALUE) & 0xFF) << 16)
                    | ((Math.clamp(vertex.blockData, 0, 255)) << 24));
            MemoryUtil.memPutInt(ptr + OFFSET_MID_BLOCK, (midBlockByte(vertex.midBlockX) & 0xFF)
                    | ((midBlockByte(vertex.midBlockY) & 0xFF) << 8) | ((midBlockByte(vertex.midBlockZ) & 0xFF) << 16)
                    | (skyLight << 24));

            return ptr + STRIDE;
        };
    }

    // Returns a section local position in sixteen bit fixed point.
    static int encodePosition(float value) {
        // Rounds to the nearest step and clamps to the range.
        return Math.clamp(Math.round((MODEL_ORIGIN + value) * MODEL_SCALE_INV), 0, POSITION_MAX_VALUE - 1);
    }

    // Returns a texture coordinate in the Nvidium pack vertex's fixed point.
    static int encodeTexture(float value) {
        return UmbraMeshVertex.encodeTexture(value);
    }

    // Returns a mid-block offset in sixty-fourths of a block as a signed byte.
    private static int midBlockByte(float value) {
        return Math.clamp(Math.round(value * 64.0f), Byte.MIN_VALUE, Byte.MAX_VALUE);
    }
}
