package com.bdmajora.equilibrium.mixin.world.entity_tick_budget;

import com.bdmajora.equilibrium.common.entity.EntityTickBudget;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// The one entry every ticked entity passes through; forceUpdate is true for the world's own per-tick pass and false for a passenger being dragged along by its mount, which is left alone
@Mixin(World.class)
public abstract class WorldMixin {
    @Inject(method = "updateEntityWithOptionalForce", at = @At("HEAD"), cancellable = true)
    private void equilibrium$governFarEntities(Entity entity, boolean forceUpdate, CallbackInfo ci) {
        if (forceUpdate && EntityTickBudget.shouldSkip((World) (Object) this, entity)) {
            ci.cancel();
        }
    }
}
