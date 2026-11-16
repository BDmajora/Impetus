package com.bdmajora.impetus.engine.impl.render.chunk.lists;

// Bit indices of the content flags of a section.
public class RenderVisualsService {
    // Bit of a section with block geometry.
    public static final int HAS_BLOCK_GEOMETRY = 0;
    // Bit of a section with animated sprites.
    public static final int HAS_SPRITES = 1;
    // Bit of a section with block entities.
    public static final int HAS_BLOCK_ENTITIES = 2;
    // Bit of a section that needs sorting while the camera moves.
    public static final int NEEDS_DYNAMIC_SORT = 3;
}
