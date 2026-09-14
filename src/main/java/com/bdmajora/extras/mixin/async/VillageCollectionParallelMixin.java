package com.bdmajora.extras.mixin.async;

import com.bdmajora.extras.async.ParallelProcessor;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.village.VillageCollection;
import org.spongepowered.asm.mixin.Mixin;

// Every villager reports its position here each tick; the list is bounded at 64 entries and drained by the main thread's village tick
@Mixin(VillageCollection.class)
public abstract class VillageCollectionParallelMixin {
    @WrapMethod(method = "addToVillagerPositionList")
    private void impetus$lockedAddPosition(BlockPos pos, Operation<Void> original) {
        if (!ParallelProcessor.INSTALLED) {
            original.call(pos);
            return;
        }
        synchronized (this) {
            original.call(pos);
        }
    }
}
