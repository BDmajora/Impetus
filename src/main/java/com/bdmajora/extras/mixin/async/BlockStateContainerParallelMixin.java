package com.bdmajora.extras.mixin.async;

import com.bdmajora.extras.async.ParallelProcessor;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.block.state.IBlockState;
import net.minecraft.world.chunk.BlockStateContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.concurrent.locks.StampedLock;

// Section palette under a StampedLock: a read that overlaps a palette resize can land on a half-swapped bit array, so reads are optimistic and retried under the read lock when a write intervened; the index-taking overloads are left alone since onResize calls them re-entrantly during a write
@Mixin(BlockStateContainer.class)
public abstract class BlockStateContainerParallelMixin {
    // Null on the client and when parallel ticking is off, so the common path costs one null check
    @Unique
    private final StampedLock impetus$lock = ParallelProcessor.INSTALLED ? new StampedLock() : null;

    @WrapMethod(method = "get(III)Lnet/minecraft/block/state/IBlockState;")
    private IBlockState impetus$lockedGet(int x, int y, int z, Operation<IBlockState> original) {
        StampedLock lock = this.impetus$lock;
        if (lock == null) {
            return original.call(x, y, z);
        }
        long stamp = lock.tryOptimisticRead();
        if (stamp != 0L) {
            try {
                IBlockState state = original.call(x, y, z);
                if (lock.validate(stamp)) {
                    return state;
                }
            } catch (Throwable ignored) {
                // A resize moved the storage out from under the read; the locked read below is authoritative
            }
        }
        stamp = lock.readLock();
        try {
            return original.call(x, y, z);
        } finally {
            lock.unlockRead(stamp);
        }
    }

    @WrapMethod(method = "set(IIILnet/minecraft/block/state/IBlockState;)V")
    private void impetus$lockedSet(int x, int y, int z, IBlockState state, Operation<Void> original) {
        StampedLock lock = this.impetus$lock;
        if (lock == null) {
            original.call(x, y, z, state);
            return;
        }
        long stamp = lock.writeLock();
        try {
            original.call(x, y, z, state);
        } finally {
            lock.unlockWrite(stamp);
        }
    }
}
