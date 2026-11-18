package com.bdmajora.extras.mixin.culling;

import com.bdmajora.extras.client.culling.Cullable;
import com.bdmajora.extras.client.culling.ModelReachHolder;
import com.bdmajora.extras.client.culling.ModelReachState;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

// Mixin that adds the occlusion state to Entity.
@Mixin(Entity.class)
public abstract class EntityCullableMixin implements Cullable, ModelReachHolder {
    // Flag set when the entity is hidden behind blocks.
    @Unique
    private volatile boolean impetus$occluded;
    // Time of the occlusion verdict.
    @Unique
    private volatile long impetus$occlusionStamp;
    // Time the worker first saw the entity hidden or zero.
    @Unique
    private long impetus$hiddenSince;
    // Equipment reach of the last measurement.
    @Unique
    private ModelReachState impetus$modelReach;

    // Returns whether the last occlusion test hid the entity.
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

    // Returns when the worker first saw the entity hidden.
    @Override
    public long impetus$hiddenSince() {
        return this.impetus$hiddenSince;
    }

    // Stores when the worker first saw the entity hidden.
    @Override
    public void impetus$setHiddenSince(long nanos) {
        this.impetus$hiddenSince = nanos;
    }

    // Returns the equipment reach of the last measurement.
    @Override
    public ModelReachState impetus$modelReach() {
        return this.impetus$modelReach;
    }

    // Stores the equipment reach.
    @Override
    public void impetus$setModelReach(ModelReachState state) {
        this.impetus$modelReach = state;
    }
}
