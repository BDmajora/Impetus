package com.bdmajora.extras.mixin.async;

import com.bdmajora.extras.async.ParallelProcessor;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.management.PlayerChunkMapEntry;
import org.spongepowered.asm.mixin.Mixin;

// Mixin that changes PlayerChunkMapEntry.
@Mixin(PlayerChunkMapEntry.class)
public abstract class PlayerChunkMapEntryParallelMixin {
    // Records a changed block under the entry's lock.
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

    // Sends the entry's changes under its lock.
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
