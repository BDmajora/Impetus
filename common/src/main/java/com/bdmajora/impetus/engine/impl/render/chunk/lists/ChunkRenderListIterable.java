package com.bdmajora.impetus.engine.impl.render.chunk.lists;

import com.bdmajora.impetus.engine.impl.render.chunk.terrain.TerrainRenderPass;

import java.util.Iterator;

public interface ChunkRenderListIterable {
    Iterator<ChunkRenderList> iterator(boolean reverse);

    int getNumRegions();

    default Iterator<ChunkRenderList> iterator() {
        return this.iterator(false);
    }

    // Whether any section needs this pass, so the caller can skip binding its program, framebuffer and state for zero draws
    default boolean hasPass(TerrainRenderPass pass) {
        return true;
    }
}
