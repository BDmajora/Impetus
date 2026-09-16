package com.bdmajora.extras.mixin.culling;

import com.bdmajora.extras.client.culling.Cullable;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;

// Two fields per entity, written by the worker and read by the render thread; volatile so the render thread sees the pair together
@Mixin(Entity.class)
public abstract class EntityCullableMixin implements Cullable {
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
