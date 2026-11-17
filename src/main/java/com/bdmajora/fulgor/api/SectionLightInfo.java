package com.bdmajora.fulgor.api;

// Section light queries Fulgor adds to ExtendedBlockStorage.
public interface SectionLightInfo {
    // Returns whether the section holds no blocks.
    boolean fulgor$hasNoBlocks();

    // Drops the cached light emptiness after the light arrays were written in place.
    void fulgor$markLightChanged();
}
