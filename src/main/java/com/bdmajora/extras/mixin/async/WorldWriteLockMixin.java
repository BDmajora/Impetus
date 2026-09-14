package com.bdmajora.extras.mixin.async;

import com.bdmajora.extras.async.ParallelWorld;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import org.spongepowered.asm.mixin.Mixin;

import java.util.Collection;

// One reentrant lock, the world monitor, around every mutation of world-level state: block writes and the notify chain they start, entity and block-entity registration; reads stay lock-free, which is why the chunk provider and explosions take this same monitor rather than their own
@Mixin(World.class)
public abstract class WorldWriteLockMixin implements ParallelWorld {
    @WrapMethod(method = "setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/state/IBlockState;I)Z")
    private boolean impetus$lockedSetBlockState(BlockPos pos, IBlockState state, int flags, Operation<Boolean> original) {
        if (!this.impetus$isParallel()) {
            return original.call(pos, state, flags);
        }
        synchronized (this) {
            return original.call(pos, state, flags);
        }
    }

    @WrapMethod(method = "markAndNotifyBlock", remap = false)
    private void impetus$lockedMarkAndNotify(BlockPos pos, Chunk chunk, IBlockState oldState, IBlockState newState, int flags, Operation<Void> original) {
        if (!this.impetus$isParallel()) {
            original.call(pos, chunk, oldState, newState, flags);
            return;
        }
        synchronized (this) {
            original.call(pos, chunk, oldState, newState, flags);
        }
    }

    // The id table lives on World and is rewritten by WorldServer's add/remove hooks under this same monitor
    @WrapMethod(method = "getEntityByID")
    private Entity impetus$lockedGetEntityByID(int id, Operation<Entity> original) {
        if (!this.impetus$isParallel()) {
            return original.call(id);
        }
        synchronized (this) {
            return original.call(id);
        }
    }

    @WrapMethod(method = "spawnEntity")
    private boolean impetus$lockedSpawnEntity(Entity entity, Operation<Boolean> original) {
        if (!this.impetus$isParallel()) {
            return original.call(entity);
        }
        synchronized (this) {
            return original.call(entity);
        }
    }

    @WrapMethod(method = "removeEntity")
    private void impetus$lockedRemoveEntity(Entity entity, Operation<Void> original) {
        if (!this.impetus$isParallel()) {
            original.call(entity);
            return;
        }
        synchronized (this) {
            original.call(entity);
        }
    }

    @WrapMethod(method = "removeEntityDangerously")
    private void impetus$lockedRemoveEntityDangerously(Entity entity, Operation<Void> original) {
        if (!this.impetus$isParallel()) {
            original.call(entity);
            return;
        }
        synchronized (this) {
            original.call(entity);
        }
    }

    @WrapMethod(method = "loadEntities")
    private void impetus$lockedLoadEntities(Collection<Entity> entities, Operation<Void> original) {
        if (!this.impetus$isParallel()) {
            original.call(entities);
            return;
        }
        synchronized (this) {
            original.call(entities);
        }
    }

    @WrapMethod(method = "unloadEntities")
    private void impetus$lockedUnloadEntities(Collection<Entity> entities, Operation<Void> original) {
        if (!this.impetus$isParallel()) {
            original.call(entities);
            return;
        }
        synchronized (this) {
            original.call(entities);
        }
    }

    @WrapMethod(method = "addTileEntity")
    private boolean impetus$lockedAddTileEntity(TileEntity tile, Operation<Boolean> original) {
        if (!this.impetus$isParallel()) {
            return original.call(tile);
        }
        synchronized (this) {
            return original.call(tile);
        }
    }

    @WrapMethod(method = "addTileEntities")
    private void impetus$lockedAddTileEntities(Collection<TileEntity> tiles, Operation<Void> original) {
        if (!this.impetus$isParallel()) {
            original.call(tiles);
            return;
        }
        synchronized (this) {
            original.call(tiles);
        }
    }

    @WrapMethod(method = "setTileEntity")
    private void impetus$lockedSetTileEntity(BlockPos pos, TileEntity tile, Operation<Void> original) {
        if (!this.impetus$isParallel()) {
            original.call(pos, tile);
            return;
        }
        synchronized (this) {
            original.call(pos, tile);
        }
    }

    @WrapMethod(method = "removeTileEntity")
    private void impetus$lockedRemoveTileEntity(BlockPos pos, Operation<Void> original) {
        if (!this.impetus$isParallel()) {
            original.call(pos);
            return;
        }
        synchronized (this) {
            original.call(pos);
        }
    }
}
