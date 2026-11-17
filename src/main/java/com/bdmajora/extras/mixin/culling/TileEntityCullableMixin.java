package com.bdmajora.extras.mixin.culling;

import com.bdmajora.extras.client.culling.Cullable;
import net.minecraft.tileentity.TileEntity;
import org.spongepowered.asm.mixin.Mixin;

// Mixin that changes TileEntity.
@Mixin(TileEntity.class)
public abstract class TileEntityCullableMixin implements Cullable {
    // Flag set when the block entity is hidden behind terrain.
    private volatile boolean impetus$occluded;
    // Frame stamp of the occlusion result.
    private volatile long impetus$occlusionStamp;

    // Returns whether the last occlusion test hid the block entity.
    @Override
    public boolean impetus$isOccluded() {
        return this.impetus$occluded;
    }

    // Records an occlusion test result and when it was taken.
    @Override
    public void impetus$setOccluded(boolean occluded, long stampNanos) {
        this.impetus$occlusionStamp = stampNanos;
        this.impetus$occluded = occluded;
    }

    // Returns when the last occlusion test ran.
    @Override
    public long impetus$occlusionStamp() {
        return this.impetus$occlusionStamp;
    }
}
