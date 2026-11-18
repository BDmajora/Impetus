package com.bdmajora.extras.mixin.culling;

import com.bdmajora.extras.client.culling.Cullable;
import com.bdmajora.extras.client.culling.OcclusionBoxHolder;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.AxisAlignedBB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

// Mixin that adds the occlusion state to TileEntity.
@Mixin(TileEntity.class)
public abstract class TileEntityCullableMixin implements Cullable, OcclusionBoxHolder {
    // Flag set when the block entity is hidden behind blocks.
    @Unique
    private volatile boolean impetus$occluded;
    // Time of the occlusion verdict.
    @Unique
    private volatile long impetus$occlusionStamp;
    // Time the worker first saw the block entity hidden or zero.
    @Unique
    private long impetus$hiddenSince;
    // Test box measured on first use.
    @Unique
    private AxisAlignedBB impetus$occlusionBox;

    // Returns whether the last occlusion test hid the block entity.
    @Override
    public boolean impetus$isOccluded() {
        return this.impetus$occluded;
    }

    // Records an occlusion verdict and when it was taken.
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

    // Returns when the worker first saw the block entity hidden.
    @Override
    public long impetus$hiddenSince() {
        return this.impetus$hiddenSince;
    }

    // Stores when the worker first saw the block entity hidden.
    @Override
    public void impetus$setHiddenSince(long nanos) {
        this.impetus$hiddenSince = nanos;
    }

    // Returns the cached test box.
    @Override
    public AxisAlignedBB impetus$occlusionBox() {
        return this.impetus$occlusionBox;
    }

    // Stores the cached test box.
    @Override
    public void impetus$setOcclusionBox(AxisAlignedBB box) {
        this.impetus$occlusionBox = box;
    }
}
