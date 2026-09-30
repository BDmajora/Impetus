package com.creativemd.littletiles.common.tileentity;

import com.creativemd.littletiles.client.render.world.TileEntityRenderManager;
import net.minecraft.tileentity.TileEntity;

import java.util.ArrayList;
import java.util.List;

// Test stand-in for LittleTiles' tile entity: the loaded flag and the quad-cache hook Impetus's compat calls, which here records its argument
public class TileEntityLittleTiles extends TileEntity {
    public TileEntityRenderManager render;
    public boolean loaded = true;
    public final List<Object> quadCacheUpdates = new ArrayList<>();

    public boolean hasLoaded() {
        return this.loaded;
    }

    public void updateQuadCache(Object chunk) {
        this.quadCacheUpdates.add(chunk);
        this.render.chunkUpdate(chunk);
    }
}
