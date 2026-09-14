package com.bdmajora.extras.mixin.async;

import com.bdmajora.extras.async.ParallelProcessor;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.management.PlayerChunkMapEntry;
import org.spongepowered.asm.mixin.Mixin;

// Block change accumulation is reached from setBlockState on any thread; its counter and position array are guarded here while the flush (update) runs from the main thread's chunk map tick
@Mixin(PlayerChunkMapEntry.class)
public abstract class PlayerChunkMapEntryParallelMixin {
    @WrapMethod(method = "blockChanged")
    private void impetus$lockedBlockChanged(int x, int y, int z, Operation<Void> original) {
        if (!ParallelProcessor.INSTALLED) {
            original.call(x, y, z);
            return;
        }
        synchronized (this) {
            original.call(x, y, z);
        }
    }

    @WrapMethod(method = "update")
    private void impetus$lockedUpdate(Operation<Void> original) {
        if (!ParallelProcessor.INSTALLED) {
            original.call();
            return;
        }
        synchronized (this) {
            original.call();
        }
    }
}
