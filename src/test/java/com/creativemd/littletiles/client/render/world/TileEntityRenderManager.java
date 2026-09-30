package com.creativemd.littletiles.client.render.world;

import com.creativemd.littletiles.client.render.cache.LayeredRenderBufferCache;

// Test stand-in for LittleTiles' per tile entity render state: a chunk update counts the light-driven rebakes it would queue, and a finished bake reports done unless its index is negative
public class TileEntityRenderManager {
    public boolean hasLightChanged;
    public int lightRebakes;
    private LayeredRenderBufferCache bufferCache;

    public TileEntityRenderManager() {
    }

    public void chunkUpdate(Object chunk) {
        if (this.hasLightChanged) {
            this.lightRebakes++;
        }
        this.hasLightChanged = false;
    }

    public boolean finishBuildingCache(int index, int renderState, boolean force) {
        return force || index >= 0;
    }

    // Lazy, since the test harness may build this without running a constructor
    public LayeredRenderBufferCache getBufferCache() {
        if (this.bufferCache == null) {
            this.bufferCache = new LayeredRenderBufferCache();
        }
        return this.bufferCache;
    }
}
