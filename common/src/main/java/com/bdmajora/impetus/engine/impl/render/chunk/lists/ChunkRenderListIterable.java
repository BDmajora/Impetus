package com.bdmajora.impetus.engine.impl.render.chunk.lists;

import com.bdmajora.impetus.engine.impl.render.chunk.terrain.TerrainRenderPass;

import java.util.Iterator;

// Render lists that can be walked in either order.
public interface ChunkRenderListIterable {
    // Returns an iterator in forward or reverse order.
    Iterator<ChunkRenderList> iterator(boolean reverse);

    // Returns the number of regions.
    int getNumRegions();

    // Returns an iterator in forward order.
    default Iterator<ChunkRenderList> iterator() {
        return this.iterator(false);
    }

    // Returns whether any section needs the pass.
    default boolean hasPass(TerrainRenderPass pass) {
        return true;
    }
}
