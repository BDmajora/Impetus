package com.bdmajora.extras.mixin.async;

import com.bdmajora.extras.async.ParallelProcessor;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.village.Village;
import org.spongepowered.asm.mixin.Mixin;

import java.util.UUID;

// Villagers and golems write reputation and aggressor lists from their own ticks; the village's per-tick maintenance runs on the main thread before entities, so guarding the entity-side writers suffices
@Mixin(Village.class)
public abstract class VillageParallelMixin {
    @WrapMethod(method = "addOrRenewAgressor")
    private void impetus$lockedAddOrRenewAggressor(EntityLivingBase entity, Operation<Void> original) {
        if (!ParallelProcessor.INSTALLED) {
            original.call(entity);
            return;
        }
        synchronized (this) {
            original.call(entity);
        }
    }

    @WrapMethod(method = "modifyPlayerReputation(Ljava/util/UUID;I)I")
    private int impetus$lockedModifyReputation(UUID player, int amount, Operation<Integer> original) {
        if (!ParallelProcessor.INSTALLED) {
            return original.call(player, amount);
        }
        synchronized (this) {
            return original.call(player, amount);
        }
    }

    @WrapMethod(method = "setDefaultPlayerReputation")
    private void impetus$lockedSetDefaultReputation(int reputation, Operation<Void> original) {
        if (!ParallelProcessor.INSTALLED) {
            original.call(reputation);
            return;
        }
        synchronized (this) {
            original.call(reputation);
        }
    }
}
