package com.bdmajora.impetus.engine.impl.render.chunk.vertex.format.impl;

import com.bdmajora.impetus.engine.api.util.ColorABGR;
import com.bdmajora.impetus.engine.impl.gl.attribute.GlVertexFormat;
import com.bdmajora.impetus.engine.impl.render.chunk.vertex.format.ChunkVertexEncoder;
import com.bdmajora.impetus.engine.impl.render.chunk.vertex.format.ChunkVertexType;

import java.util.Map;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;
import com.bdmajora.impetus.engine.impl.common.util.MathUtil;

// 16-byte terrain vertex read by the mesh shader as one uvec4: shade folded into 24-bit colour on the CPU, light in two bytes; the GlVertexFormat exists only because ChunkVertexType demands one
public class MeshChunkVertex implements ChunkVertexType {
    public static final int STRIDE = 16;

    public static final GlVertexFormat VERTEX_FORMAT = GlVertexFormat.builder(STRIDE).build();

    private static final int POSITION_MAX_VALUE = 65536;
    private static final int TEXTURE_MAX_VALUE = 32768;

    private static final float MODEL_ORIGIN = 8.0f;
    private static final float MODEL_RANGE = 32.0f;
    private static final float MODEL_SCALE = MODEL_RANGE / POSITION_MAX_VALUE;
    private static final float MODEL_SCALE_INV = POSITION_MAX_VALUE / MODEL_RANGE;

    private static final float TEXTURE_SCALE = 1.0f / TEXTURE_MAX_VALUE;

    // Packed units per block, i.e. 65536 / 32
    private static final int UNITS_PER_BLOCK = (int) (MODEL_SCALE_INV);
    // Where a section-local coordinate of 0 lands in packed units
    private static final int PACKED_ORIGIN = (int) (MODEL_ORIGIN * MODEL_SCALE_INV);

    public static final MeshChunkVertex INSTANCE = new MeshChunkVertex();

    // Positions are packed integers over the section
    @Override
    public float getPositionScale() {
        return MODEL_SCALE;
    }

    // Shift so a small negative margin fits
    @Override
    public float getPositionOffset() {
        return -MODEL_ORIGIN;
    }

    // UVs are packed integers
    @Override
    public float getTextureScale() {
        return TEXTURE_SCALE;
    }

    // Layout the mesh shader unpacks
    @Override
    public GlVertexFormat getVertexFormat() {
        return VERTEX_FORMAT;
    }

    // Writes one packed vertex per call
    @Override
    public ChunkVertexEncoder createEncoder() {
        return (ptr, material, vertex, sectionIndex) -> {
            int light = packLight(vertex.light);

            LWJGL.memPutInt(ptr + 0, encodePosition(vertex.x) | (encodePosition(vertex.y) << 16));
            LWJGL.memPutInt(ptr + 4, encodePosition(vertex.z) | ((material.bits() & 0xFF) << 16) | ((light & 0xFF) << 24));
            LWJGL.memPutInt(ptr + 8, packShadedColor(vertex.color) | (((light >> 8) & 0xFF) << 24));
            LWJGL.memPutInt(ptr + 12, encodeTexture(vertex.u) | (encodeTexture(vertex.v) << 16));

            return ptr + STRIDE;
        };
    }

    // Tells the shader the mesh layout is in use
    @Override
    public Map<String, String> getDefines() {
        var map = ChunkVertexType.super.getDefines();
        map.put("USE_VERTEX_COMPRESSION", "");
        map.put("TEXTURE_MAX_SCALE", String.valueOf(TEXTURE_MAX_VALUE));
        return map;
    }

    // ---- readers, for the section bounding box ----

    public static int readPackedX(long ptr) {
        return LWJGL.memGetInt(ptr) & 0xFFFF;
    }

    // Reads the packed y field from a vertex in memory
    public static int readPackedY(long ptr) {
        return (LWJGL.memGetInt(ptr) >>> 16) & 0xFFFF;
    }

    // Reads the packed z field
    public static int readPackedZ(long ptr) {
        return LWJGL.memGetInt(ptr + 4) & 0xFFFF;
    }

    // Packed position back to its 1-block cell in the section, clamped to 0..15 since vertices may sit slightly outside the section (fence posts) and the occlusion box is per-section
    public static int decodeBlockCoord(int packed) {
        int block = (packed - PACKED_ORIGIN) / UNITS_PER_BLOCK;
        return Math.max(0, Math.min(15, block));
    }

    // Back to float
    public static float decodePosition(int packed) {
        return (packed / MODEL_SCALE_INV) - MODEL_ORIGIN;
    }

    // Float to packed integer
    private static int encodePosition(float value) {
        return ((int) ((MODEL_ORIGIN + value) * MODEL_SCALE_INV)) & 0xFFFF;
    }

    // Float UV to packed integer
    private static int encodeTexture(float value) {
        return Math.round(value * TEXTURE_MAX_VALUE) & 0xFFFF;
    }

    // Folds the shade factor (stashed in alpha by the mesher) into RGB, freeing the fourth byte for sky light; integer scaling truncates like the float path did
    private static int packShadedColor(int color) {
        int shade = ColorABGR.unpackAlpha(color);
        int r = (ColorABGR.unpackRed(color) * shade) / 255;
        int g = (ColorABGR.unpackGreen(color) * shade) / 255;
        int b = (ColorABGR.unpackBlue(color) * shade) / 255;

        return ColorABGR.pack(r, g, b, 0x00);
    }

    // 16-bit lightmap coordinates down to a byte each, clamped to 8..248 so rounding never lands on the lightmap's outermost texels (same reason it samples CLAMP_TO_EDGE)
    private static int packLight(int light) {
        int sky = MathUtil.clamp((light >>> 16) & 0xFF, 8, 248);
        int block = MathUtil.clamp(light & 0xFF, 8, 248);

        return block | (sky << 8);
    }
}
