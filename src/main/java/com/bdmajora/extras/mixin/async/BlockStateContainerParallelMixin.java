package com.bdmajora.extras.mixin.async;

import com.bdmajora.extras.async.ParallelBlockStateContainer;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.block.state.IBlockState;
import net.minecraft.world.chunk.BlockStateContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.concurrent.locks.StampedLock;

// Mixin that changes BlockStateContainer.
@Mixin(BlockStateContainer.class)
public abstract class BlockStateContainerParallelMixin implements ParallelBlockStateContainer {
    // Field lock added to the target.
    @Unique
    private volatile StampedLock impetus$lock;

    // Handles enable parallel lock.
    @Override
    public void impetus$enableParallelLock() {
        if (this.impetus$lock == null) {
            this.impetus$lock = new StampedLock();
        }
    }

    // Handles locked get.
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
            }
        }
        stamp = lock.readLock();
        try {
            return original.call(x, y, z);
        } finally {
            lock.unlockRead(stamp);
        }
    }

    // Handles locked set.
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
