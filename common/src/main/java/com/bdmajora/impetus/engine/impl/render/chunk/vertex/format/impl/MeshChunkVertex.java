package com.bdmajora.impetus.engine.impl.render.chunk.vertex.format.impl;

import com.bdmajora.impetus.engine.api.util.ColorABGR;
import com.bdmajora.impetus.engine.impl.gl.attribute.GlVertexAttributeFormat;
import com.bdmajora.impetus.engine.impl.gl.attribute.GlVertexFormat;
import com.bdmajora.impetus.engine.impl.render.chunk.vertex.format.ChunkVertexEncoder;

import java.util.Map;

import com.bdmajora.impetus.engine.impl.common.util.MathUtil;
import org.lwjgl.system.MemoryUtil;

// Terrain vertex of 16 bytes that the mesh shader reads as one vector.
public class MeshChunkVertex implements MeshVertexLayout {
    // Bytes of one vertex.
    public static final int STRIDE = 16;

    // GL format of the vertex.
    public static final GlVertexFormat VERTEX_FORMAT = GlVertexFormat.builder(STRIDE)
            .addElement("a_Packed", 0, GlVertexAttributeFormat.UNSIGNED_INT, 4, false, true)
            .build();

    // Number of position values.
    private static final int POSITION_MAX_VALUE = 65536;
    // Value of the texture coordinate one.
    public static final int TEXTURE_MAX_VALUE = 32768;

    // Offset that makes positions unsigned.
    private static final float MODEL_ORIGIN = 8.0f;
    // Range of positions in blocks.
    private static final float MODEL_RANGE = 32.0f;
    // Blocks per position step.
    private static final float MODEL_SCALE = MODEL_RANGE / POSITION_MAX_VALUE;
    // Position steps per block.
    private static final float MODEL_SCALE_INV = POSITION_MAX_VALUE / MODEL_RANGE;

    // Texture coordinate per step.
    private static final float TEXTURE_SCALE = 1.0f / TEXTURE_MAX_VALUE;

    // Position steps per block.
    private static final int UNITS_PER_BLOCK = (int) (MODEL_SCALE_INV);
    // Packed position of the section origin.
    private static final int PACKED_ORIGIN = (int) (MODEL_ORIGIN * MODEL_SCALE_INV);

    // Shared instance.
    public static final MeshChunkVertex INSTANCE = new MeshChunkVertex();

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

    // Returns the texture scale.
    @Override
    public float getTextureScale() {
        return TEXTURE_SCALE;
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
            int light = packLight(vertex.light);

            MemoryUtil.memPutInt(ptr + 0, encodePosition(vertex.x) | (encodePosition(vertex.y) << 16));
            MemoryUtil.memPutInt(ptr + 4, encodePosition(vertex.z) | ((material.bits() & 0xFF) << 16) | ((light & 0xFF) << 24));
            MemoryUtil.memPutInt(ptr + 8, packShadedColor(vertex.color) | (((light >> 8) & 0xFF) << 24));
            MemoryUtil.memPutInt(ptr + 12, encodeTexture(vertex.u) | (encodeTexture(vertex.v) << 16));

            return ptr + STRIDE;
        };
    }

    // Returns the shader definitions including the mesh layout flag.
    @Override
    public Map<String, String> getDefines() {
        var map = MeshVertexLayout.super.getDefines();
        map.put("USE_VERTEX_COMPRESSION", "");
        map.put("TEXTURE_MAX_SCALE", String.valueOf(TEXTURE_MAX_VALUE));
        return map;
    }

    // Reads the packed x of a vertex.
    public static int readPackedX(long ptr) {
        return MemoryUtil.memGetInt(ptr) & 0xFFFF;
    }

    // Reads the packed y of a vertex.
    public static int readPackedY(long ptr) {
        return (MemoryUtil.memGetInt(ptr) >>> 16) & 0xFFFF;
    }

    // Reads the packed z of a vertex.
    public static int readPackedZ(long ptr) {
        return MemoryUtil.memGetInt(ptr + 4) & 0xFFFF;
    }

    // Returns the block coordinate of a packed position limited to the section.
    public static int decodeBlockCoord(int packed) {
        int block = (packed - PACKED_ORIGIN) / UNITS_PER_BLOCK;
        return Math.max(0, Math.min(15, block));
    }

    // Returns the coordinate of a packed position.
    public static float decodePosition(int packed) {
        return (packed / MODEL_SCALE_INV) - MODEL_ORIGIN;
    }

    // Returns the packed position of a coordinate.
    public static int encodePosition(float value) {
        return ((int) ((MODEL_ORIGIN + value) * MODEL_SCALE_INV)) & 0xFFFF;
    }

    // Returns the packed texture coordinate.
    private static int encodeTexture(float value) {
        return Math.round(value * TEXTURE_MAX_VALUE) & 0xFFFF;
    }

    // Returns the color with the shade folded into the channels.
    private static int packShadedColor(int color) {
        int shade = ColorABGR.unpackAlpha(color);
        int r = (ColorABGR.unpackRed(color) * shade) / 255;
        int g = (ColorABGR.unpackGreen(color) * shade) / 255;
        int b = (ColorABGR.unpackBlue(color) * shade) / 255;

        return ColorABGR.pack(r, g, b, 0x00);
    }

    // Returns the light packed into two bytes.
    private static int packLight(int light) {
        int sky = MathUtil.clamp((light >>> 16) & 0xFF, 8, 248);
        int block = MathUtil.clamp(light & 0xFF, 8, 248);

        return block | (sky << 8);
    }
}
