package com.bdmajora.extras.mixin.async;

import com.bdmajora.extras.async.ParallelBlockStateContainer;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.block.state.IBlockState;
import net.minecraft.world.chunk.BlockStateContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.concurrent.locks.StampedLock;

// Section palette under a StampedLock: a read that overlaps a palette resize can land on a half-swapped bit array, so reads are optimistic and retried under the read lock when a write intervened; the index-taking overloads are left alone since onResize calls them re-entrantly during a write
@Mixin(BlockStateContainer.class)
public abstract class BlockStateContainerParallelMixin implements ParallelBlockStateContainer {
    // Null until ChunkParallelMixin enables it for a section of a parallel server world, so every client section and every section of a world that is not ticked in parallel keeps the lock-free read; the container has no world reference, and gating on the install flag alone put the optimistic read and its fence on every block read of the client (the mesher, particles, entity collision) for nothing. Volatile so a section published to the workers right after creation sees the lock, at the cost of a plain load on x86
    @Unique
    private volatile StampedLock impetus$lock;

    // Called once, before the section is reachable from another thread
    @Override
    public void impetus$enableParallelLock() {
        if (this.impetus$lock == null) {
            this.impetus$lock = new StampedLock();
        }
    }

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
