package com.bdmajora.extras.mixin.culling;

import com.bdmajora.extras.client.culling.Cullable;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;

// Mixin that changes Entity.
@Mixin(Entity.class)
public abstract class EntityCullableMixin implements Cullable {
    // Flag set when the entity is hidden behind terrain.
    private volatile boolean impetus$occluded;
    // Frame stamp of the occlusion result.
    private volatile long impetus$occlusionStamp;

    // Returns whether occluded.
    @Override
    public boolean impetus$isOccluded() {
        return this.impetus$occluded;
    }

    // Handles set occluded.
    @Override
    public void impetus$setOccluded(boolean occluded, long stampNanos) {
        this.impetus$occlusionStamp = stampNanos;
        this.impetus$occluded = occluded;
    }

    // Handles occlusion stamp.
    @Override
    public long impetus$occlusionStamp() {
        return this.impetus$occlusionStamp;
    }
}
