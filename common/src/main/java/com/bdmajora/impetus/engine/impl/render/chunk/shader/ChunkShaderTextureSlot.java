package com.bdmajora.impetus.engine.impl.render.chunk.shader;

// Texture slots of the chunk shader.
public enum ChunkShaderTextureSlot {
    // Slot of the block atlas.
    BLOCK,
    // Slot of the lightmap.
    LIGHT;

    // Every slot.
    public static final ChunkShaderTextureSlot[] VALUES = ChunkShaderTextureSlot.values();
}
