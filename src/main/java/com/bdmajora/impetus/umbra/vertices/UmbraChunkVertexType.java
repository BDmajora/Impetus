package com.bdmajora.impetus.umbra.vertices;

import com.bdmajora.impetus.engine.impl.gl.attribute.GlVertexAttributeFormat;
import com.bdmajora.impetus.engine.impl.gl.attribute.GlVertexFormat;
import com.bdmajora.impetus.engine.impl.render.chunk.vertex.format.ChunkVertexEncoder;
import com.bdmajora.impetus.engine.impl.render.chunk.vertex.format.impl.VanillaLikeChunkVertex;
import org.lwjgl.system.MemoryUtil;

// Chunk vertex format of pack terrain.
public class UmbraChunkVertexType extends VanillaLikeChunkVertex {
    // Shared instance.
    public static final UmbraChunkVertexType INSTANCE = new UmbraChunkVertexType();

    // Bytes of one vertex.
    public static final int STRIDE = 56;

    // Byte offset of the normal.
    private static final int OFFSET_NORMAL = 28;
    // Byte offset of the tangent.
    private static final int OFFSET_TANGENT = 32;
    // Byte offset of the texture center.
    private static final int OFFSET_MID_TEX = 36;
    // Byte offset of the block id.
    private static final int OFFSET_ENTITY = 44;
    // Byte offset of the block center.
    private static final int OFFSET_MID_BLOCK = 52;

    // Vertex format.
    public static final GlVertexFormat VERTEX_FORMAT = baseFormat(STRIDE)
            .addElement("iris_Normal", OFFSET_NORMAL, GlVertexAttributeFormat.BYTE, 4, true, false)
            .addElement("iris_Tangent", OFFSET_TANGENT, GlVertexAttributeFormat.BYTE, 4, true, false)
            .addElement("iris_MidTexCoord", OFFSET_MID_TEX, GlVertexAttributeFormat.FLOAT, 2, false, false)
            .addElement("iris_BlockInfo", OFFSET_ENTITY, GlVertexAttributeFormat.SHORT, 4, false, false)
            .addElement("iris_MidBlock", OFFSET_MID_BLOCK, GlVertexAttributeFormat.BYTE, 4, false, false)
            .build();

    // Prevents instantiation
    private UmbraChunkVertexType() {
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
            writeBase(ptr, material, vertex, sectionIndex);

            MemoryUtil.memPutInt(ptr + OFFSET_NORMAL, vertex.trueNormal);
            MemoryUtil.memPutInt(ptr + OFFSET_TANGENT, vertex.tangent);
            MemoryUtil.memPutFloat(ptr + OFFSET_MID_TEX, vertex.midTexU);
            MemoryUtil.memPutFloat(ptr + OFFSET_MID_TEX + 4, vertex.midTexV);
            MemoryUtil.memPutShort(ptr + OFFSET_ENTITY, clampShort(vertex.blockId));
            MemoryUtil.memPutShort(ptr + OFFSET_ENTITY + 2, clampShort(vertex.blockRenderType));
            MemoryUtil.memPutShort(ptr + OFFSET_ENTITY + 4, clampShort(vertex.blockData));
            MemoryUtil.memPutShort(ptr + OFFSET_ENTITY + 6, (short) 1);

            int mbx = clampByte(Math.round(vertex.midBlockX * 64.0f));
            int mby = clampByte(Math.round(vertex.midBlockY * 64.0f));
            int mbz = clampByte(Math.round(vertex.midBlockZ * 64.0f));
            int mbe = clampUnsignedByte(vertex.blockEmission);
            MemoryUtil.memPutInt(ptr + OFFSET_MID_BLOCK, (mbx & 0xFF) | ((mby & 0xFF) << 8) | ((mbz & 0xFF) << 16) | ((mbe & 0xFF) << 24));

            return ptr + STRIDE;
        };
    }

    // Limits the byte.
    private static int clampByte(int value) {
        return value < -128 ? -128 : (value > 127 ? 127 : value);
    }

    // Limits the unsigned byte.
    private static int clampUnsignedByte(int value) {
        return value < 0 ? 0 : (value > 255 ? 255 : value);
    }

    // Limits the short.
    private static short clampShort(int value) {
        return (short) (value < Short.MIN_VALUE ? Short.MIN_VALUE : Math.min(value, Short.MAX_VALUE));
    }
}
