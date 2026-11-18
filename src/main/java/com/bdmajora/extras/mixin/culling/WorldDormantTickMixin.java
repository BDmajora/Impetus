package com.bdmajora.extras.mixin.culling;

import com.bdmajora.extras.client.culling.DormantTicks;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// Mixin that swaps the client tick of a hidden armor stand for a light tick.
@Mixin(World.class)
public abstract class WorldDormantTickMixin {
    // Runs the light tick instead of onUpdate for a paused entity.
    @WrapOperation(method = "updateEntityWithOptionalForce", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;onUpdate()V"))
    private void impetus$pauseHidden(Entity entity, Operation<Void> original) {
        if (DormantTicks.shouldPause(entity)) {
            DormantTicks.tick((EntityArmorStand) entity);
        } else {
            original.call(entity);
        }
    }
}
