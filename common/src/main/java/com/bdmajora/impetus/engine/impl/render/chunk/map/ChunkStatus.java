package com.bdmajora.impetus.engine.impl.render.chunk.map;

// Flags tracked for every chunk position.
public class ChunkStatus {
    // Flag of a chunk with block data.
    public static final int FLAG_HAS_BLOCK_DATA = 1;
    // Flag of a chunk with light data.
    public static final int FLAG_HAS_LIGHT_DATA = 2;
    // Both flags.
    public static final int FLAG_ALL = FLAG_HAS_BLOCK_DATA | FLAG_HAS_LIGHT_DATA;
}