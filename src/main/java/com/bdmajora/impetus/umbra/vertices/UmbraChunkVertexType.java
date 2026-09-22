package com.bdmajora.impetus.umbra.vertices;

import com.bdmajora.impetus.engine.impl.gl.attribute.GlVertexAttributeFormat;
import com.bdmajora.impetus.engine.impl.gl.attribute.GlVertexFormat;
import com.bdmajora.impetus.engine.impl.render.chunk.vertex.format.ChunkVertexEncoder;
import com.bdmajora.impetus.engine.impl.render.chunk.vertex.format.impl.VanillaLikeChunkVertex;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// The terrain vertex format while a pack is active: VanillaLikeChunkVertex's layout plus the OptiFine per-vertex attributes (normal, at_tangent, mc_midTexCoord, mc_Entity); only selected with a pack loaded, so the wider stride costs nothing otherwise
public class UmbraChunkVertexType extends VanillaLikeChunkVertex {
    public static final UmbraChunkVertexType INSTANCE = new UmbraChunkVertexType();

    public static final int STRIDE = 56;

    // Offsets after the 28-byte vanilla-like base.
    private static final int OFFSET_NORMAL = 28;   // NormI8-packed face normal (4 normalized signed bytes)
    private static final int OFFSET_TANGENT = 32;  // NormI8-packed tangent, w = handedness
    private static final int OFFSET_MID_TEX = 36;  // 2 x float, quad texture centre in atlas UV space
    private static final int OFFSET_ENTITY = 44;   // 4 x short, mc_Entity.xyzw (see class doc)
    private static final int OFFSET_MID_BLOCK = 52; // at_midBlock: 3 signed bytes (offset * 64) + emission byte

    public static final GlVertexFormat VERTEX_FORMAT = baseFormat(STRIDE)
            .addElement("iris_Normal", OFFSET_NORMAL, GlVertexAttributeFormat.BYTE, 4, true, false)
            .addElement("iris_Tangent", OFFSET_TANGENT, GlVertexAttributeFormat.BYTE, 4, true, false)
            .addElement("iris_MidTexCoord", OFFSET_MID_TEX, GlVertexAttributeFormat.FLOAT, 2, false, false)
            .addElement("iris_BlockInfo", OFFSET_ENTITY, GlVertexAttributeFormat.SHORT, 4, false, false)
            // at_midBlock: raw bytes. xyz are signed *64-scaled offsets, w is block emission.
            .addElement("iris_MidBlock", OFFSET_MID_BLOCK, GlVertexAttributeFormat.BYTE, 4, false, false)
            .build();

    private UmbraChunkVertexType() {
    }

    // The attribute layout, including the OptiFine extras
    @Override
    public GlVertexFormat getVertexFormat() {
        return VERTEX_FORMAT;
    }

    // Writes one vertex per call, pulling the shader fields the mesher filled
    @Override
    public ChunkVertexEncoder createEncoder() {
        return (ptr, material, vertex, sectionIndex) -> {
            // Base layout identical to VanillaLikeChunkVertex (the transformer prologue decodes this).
            writeBase(ptr, material, vertex, sectionIndex);

            // OptiFine extended attributes, filled in by the meshing pipeline while shaders are active.
            LWJGL.memPutInt(ptr + OFFSET_NORMAL, vertex.trueNormal);
            LWJGL.memPutInt(ptr + OFFSET_TANGENT, vertex.tangent);
            LWJGL.memPutFloat(ptr + OFFSET_MID_TEX, vertex.midTexU);
            LWJGL.memPutFloat(ptr + OFFSET_MID_TEX + 4, vertex.midTexV);
            LWJGL.memPutShort(ptr + OFFSET_ENTITY, clampShort(vertex.blockId));
            LWJGL.memPutShort(ptr + OFFSET_ENTITY + 2, clampShort(vertex.blockRenderType));
            LWJGL.memPutShort(ptr + OFFSET_ENTITY + 4, clampShort(vertex.blockData));
            LWJGL.memPutShort(ptr + OFFSET_ENTITY + 6, (short) 1);

            // at_midBlock: offset-to-block-center (block units) * 64 plus block emission in w, like upstream Umbra.
            int mbx = clampByte(Math.round(vertex.midBlockX * 64.0f));
            int mby = clampByte(Math.round(vertex.midBlockY * 64.0f));
            int mbz = clampByte(Math.round(vertex.midBlockZ * 64.0f));
            int mbe = clampUnsignedByte(vertex.blockEmission);
            LWJGL.memPutInt(ptr + OFFSET_MID_BLOCK, (mbx & 0xFF) | ((mby & 0xFF) << 8) | ((mbz & 0xFF) << 16) | ((mbe & 0xFF) << 24));

            return ptr + STRIDE;
        };
    }

    // -128..127
    private static int clampByte(int value) {
        return value < -128 ? -128 : (value > 127 ? 127 : value);
    }

    // 0..255
    private static int clampUnsignedByte(int value) {
        return value < 0 ? 0 : (value > 255 ? 255 : value);
    }

    // Short range
    private static short clampShort(int value) {
        return (short) (value < Short.MIN_VALUE ? Short.MIN_VALUE : Math.min(value, Short.MAX_VALUE));
    }
}
