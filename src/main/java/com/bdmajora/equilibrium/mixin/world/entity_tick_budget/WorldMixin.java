package com.bdmajora.equilibrium.mixin.world.entity_tick_budget;

import com.bdmajora.equilibrium.common.entity.EntityTickBudget;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Mixin that changes World.
@Mixin(World.class)
public abstract class WorldMixin {
    // Runs at the start of updateEntityWithOptionalForce and can cancel it.
    @Inject(method = "updateEntityWithOptionalForce", at = @At("HEAD"), cancellable = true)
    private void equilibrium$governFarEntities(Entity entity, boolean forceUpdate, CallbackInfo ci) {
        if (forceUpdate && EntityTickBudget.shouldSkip((World) (Object) this, entity)) {
            ci.cancel();
        }
    }
}
