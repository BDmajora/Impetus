package com.bdmajora.extras.mixin.culling;

import com.bdmajora.extras.client.culling.Cullable;
import net.minecraft.tileentity.TileEntity;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(TileEntity.class)
public abstract class TileEntityCullableMixin implements Cullable {
    private volatile boolean impetus$occluded;
    private volatile long impetus$occlusionStamp;

    @Override
    public boolean impetus$isOccluded() {
        return this.impetus$occluded;
    }

    @Override
    public void impetus$setOccluded(boolean occluded, long stampNanos) {
        this.impetus$occlusionStamp = stampNanos;
        this.impetus$occluded = occluded;
    }

    @Override
    public long impetus$occlusionStamp() {
        return this.impetus$occlusionStamp;
    }
}
