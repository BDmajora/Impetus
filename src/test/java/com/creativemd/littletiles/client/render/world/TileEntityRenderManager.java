package com.creativemd.littletiles.client.render.world;

import com.creativemd.littletiles.client.render.cache.LayeredRenderBufferCache;

// Test stand-in for LittleTiles' render state.
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

    // Returns the buffer cache and creates it on first use.
    public LayeredRenderBufferCache getBufferCache() {
        if (this.bufferCache == null) {
            this.bufferCache = new LayeredRenderBufferCache();
        }
        return this.bufferCache;
    }
}
