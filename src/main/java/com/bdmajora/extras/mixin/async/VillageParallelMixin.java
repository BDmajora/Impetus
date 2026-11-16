package com.bdmajora.extras.mixin.async;

import com.bdmajora.extras.async.ParallelProcessor;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.village.Village;
import org.spongepowered.asm.mixin.Mixin;

import java.util.UUID;

// Mixin that changes Village.
@Mixin(Village.class)
public abstract class VillageParallelMixin {
    // Handles locked add or renew aggressor.
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

    // Handles locked modify reputation.
    @WrapMethod(method = "modifyPlayerReputation(Ljava/util/UUID;I)I")
    private int impetus$lockedModifyReputation(UUID player, int amount, Operation<Integer> original) {
        if (!ParallelProcessor.INSTALLED) {
            return original.call(player, amount);
        }
        synchronized (this) {
            return original.call(player, amount);
        }
    }

    // Handles locked set default reputation.
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
