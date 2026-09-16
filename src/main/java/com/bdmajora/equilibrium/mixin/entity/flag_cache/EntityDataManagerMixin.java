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

// Every write path into the data manager, local set() and the network's setEntryValues(), drops the owner's cached flags; invalidating on any key rather than only FLAGS keeps this to one boolean store with no key comparison
@Mixin(EntityDataManager.class)
public abstract class EntityDataManagerMixin {
    @Shadow
    @Final
    private Entity entity;

    @Inject(method = "set", at = @At("RETURN"))
    private <T> void equilibrium$invalidateOnSet(DataParameter<T> key, T value, CallbackInfo ci) {
        ((FlagCache) this.entity).equilibrium$invalidateFlags();
    }

    @Inject(method = "setEntryValues", at = @At("RETURN"))
    private void equilibrium$invalidateOnSync(List<EntityDataManager.DataEntry<?>> entries, CallbackInfo ci) {
        ((FlagCache) this.entity).equilibrium$invalidateFlags();
    }
}
