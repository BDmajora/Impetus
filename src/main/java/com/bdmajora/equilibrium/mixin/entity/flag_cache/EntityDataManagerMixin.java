package com.bdmajora.equilibrium.mixin.entity.flag_cache;

import com.bdmajora.equilibrium.common.entity.FlagCache;
import net.minecraft.entity.Entity;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.EntityDataManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

// Mixin that changes EntityDataManager.
@Mixin(EntityDataManager.class)
public abstract class EntityDataManagerMixin {
    // Reference to the field entity of the target.
    @Shadow
    @Final
    private Entity entity;

    // Runs before every return of set.
    @Inject(method = "set", at = @At("RETURN"))
    private <T> void equilibrium$invalidateOnSet(DataParameter<T> key, T value, CallbackInfo ci) {
        ((FlagCache) this.entity).equilibrium$invalidateFlags();
    }

    // Runs before every return of setEntryValues.
    @Inject(method = "setEntryValues", at = @At("RETURN"))
    private void equilibrium$invalidateOnSync(List<EntityDataManager.DataEntry<?>> entries, CallbackInfo ci) {
        ((FlagCache) this.entity).equilibrium$invalidateFlags();
    }
}
