package com.creativemd.littletiles.client.render.cache;

// Test stand-in for LittleTiles' per-layer bake results, filled directly by the test
public class LayeredRenderBufferCache {
    private final IRenderDataCache[] layers = new IRenderDataCache[4];

    public IRenderDataCache get(int layer) {
        return this.layers[layer];
    }

    public void put(int layer, IRenderDataCache data) {
        this.layers[layer] = data;
    }
}
