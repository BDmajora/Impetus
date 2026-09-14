package com.bdmajora.extras.mixin.async;

import com.bdmajora.extras.async.ParallelProcessor;
import com.bdmajora.extras.async.ParallelWorld;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Explosion;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

// The server-only mutable state behind the world monitor (id and uuid tables, scheduled ticks, block events, explosions), plus random ticks collected in vanilla's LCG order on the main thread and executed as a parallel batch
@Mixin(WorldServer.class)
public abstract class WorldServerParallelMixin implements ParallelWorld {
    // Random ticks run off-thread get a per-thread source, since the block's updateTick may draw from it freely
    @Unique
    private static final ThreadLocal<Random> impetus$randomTickRandom = ThreadLocal.withInitial(Random::new);

    @Unique
    private List<Runnable> impetus$randomTickBatch;

    @WrapMethod(method = "onEntityAdded")
    private void impetus$lockedOnEntityAdded(Entity entity, Operation<Void> original) {
        if (!this.impetus$isParallel()) {
            original.call(entity);
            return;
        }
        synchronized (this) {
            original.call(entity);
        }
    }

    @WrapMethod(method = "onEntityRemoved")
    private void impetus$lockedOnEntityRemoved(Entity entity, Operation<Void> original) {
        if (!this.impetus$isParallel()) {
            original.call(entity);
            return;
        }
        synchronized (this) {
            original.call(entity);
        }
    }

    @WrapMethod(method = "getEntityFromUuid")
    private Entity impetus$lockedGetEntityFromUuid(UUID uuid, Operation<Entity> original) {
        if (!this.impetus$isParallel()) {
            return original.call(uuid);
        }
        synchronized (this) {
            return original.call(uuid);
        }
    }

    @WrapMethod(method = "updateBlockTick")
    private void impetus$lockedUpdateBlockTick(BlockPos pos, Block block, int delay, int priority, Operation<Void> original) {
        if (!this.impetus$isParallel()) {
            original.call(pos, block, delay, priority);
            return;
        }
        synchronized (this) {
            original.call(pos, block, delay, priority);
        }
    }

    @WrapMethod(method = "scheduleBlockUpdate")
    private void impetus$lockedScheduleBlockUpdate(BlockPos pos, Block block, int delay, int priority, Operation<Void> original) {
        if (!this.impetus$isParallel()) {
            original.call(pos, block, delay, priority);
            return;
        }
        synchronized (this) {
            original.call(pos, block, delay, priority);
        }
    }

    @WrapMethod(method = "isBlockTickPending")
    private boolean impetus$lockedIsBlockTickPending(BlockPos pos, Block block, Operation<Boolean> original) {
        if (!this.impetus$isParallel()) {
            return original.call(pos, block);
        }
        synchronized (this) {
            return original.call(pos, block);
        }
    }

    @WrapMethod(method = "isUpdateScheduled")
    private boolean impetus$lockedIsUpdateScheduled(BlockPos pos, Block block, Operation<Boolean> original) {
        if (!this.impetus$isParallel()) {
            return original.call(pos, block);
        }
        synchronized (this) {
            return original.call(pos, block);
        }
    }

    @WrapMethod(method = "tickUpdates")
    private boolean impetus$lockedTickUpdates(boolean runAllPending, Operation<Boolean> original) {
        if (!this.impetus$isParallel()) {
            return original.call(runAllPending);
        }
        synchronized (this) {
            return original.call(runAllPending);
        }
    }

    @WrapMethod(method = "addBlockEvent")
    private void impetus$lockedAddBlockEvent(BlockPos pos, Block block, int eventId, int eventParam, Operation<Void> original) {
        if (!this.impetus$isParallel()) {
            original.call(pos, block, eventId, eventParam);
            return;
        }
        synchronized (this) {
            original.call(pos, block, eventId, eventParam);
        }
    }

    @WrapMethod(method = "sendQueuedBlockEvents")
    private void impetus$lockedSendQueuedBlockEvents(Operation<Void> original) {
        if (!this.impetus$isParallel()) {
            original.call();
            return;
        }
        synchronized (this) {
            original.call();
        }
    }

    @WrapMethod(method = "newExplosion")
    private Explosion impetus$lockedNewExplosion(Entity source, double x, double y, double z, float strength, boolean causesFire, boolean damagesTerrain, Operation<Explosion> original) {
        if (!this.impetus$isParallel()) {
            return original.call(source, x, y, z, strength, causesFire, damagesTerrain);
        }
        synchronized (this) {
            return original.call(source, x, y, z, strength, causesFire, damagesTerrain);
        }
    }

    // Random ticks: the LCG that picks positions stays sequential on the main thread, only the block's reaction is deferred
    @WrapOperation(method = "updateBlocks", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/block/Block;randomTick(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/state/IBlockState;Ljava/util/Random;)V"))
    private void impetus$collectRandomTick(Block block, World world, BlockPos pos, IBlockState state, Random random, Operation<Void> original) {
        if (!ParallelProcessor.isRandomTickingActive(world)) {
            original.call(block, world, pos, state, random);
            return;
        }
        List<Runnable> batch = this.impetus$randomTickBatch;
        if (batch == null) {
            batch = new ArrayList<>();
            this.impetus$randomTickBatch = batch;
        }
        // The direct call rather than the Operation, which must not outlive the handler
        batch.add(() -> block.randomTick(world, pos, state, impetus$randomTickRandom.get()));
    }

    // Flushes the collected random ticks through the pool once the chunk sweep is done
    @Inject(method = "updateBlocks", at = @At("TAIL"))
    private void impetus$flushRandomTicks(CallbackInfo ci) {
        List<Runnable> batch = this.impetus$randomTickBatch;
        if (batch == null) {
            return;
        }
        this.impetus$randomTickBatch = null;
        ParallelProcessor.forEachParallel(batch, ParallelProcessor.RANDOM_TICK_COST, Runnable::run);
    }
}
