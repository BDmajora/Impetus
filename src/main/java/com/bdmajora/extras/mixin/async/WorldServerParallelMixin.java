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

// Mixin that changes WorldServer.
@Mixin(WorldServer.class)
public abstract class WorldServerParallelMixin implements ParallelWorld {
    // Random of each random tick worker.
    @Unique
    private static final ThreadLocal<Random> impetus$randomTickRandom = ThreadLocal.withInitial(Random::new);

    // Random ticks collected this tick.
    @Unique
    private List<Runnable> impetus$randomTickBatch;

    // Adds an entity under the world lock.
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

    // Removes an entity under the world lock.
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

    // Looks an entity up by UUID under the world lock.
    @WrapMethod(method = "getEntityFromUuid")
    private Entity impetus$lockedGetEntityFromUuid(UUID uuid, Operation<Entity> original) {
        if (!this.impetus$isParallel()) {
            return original.call(uuid);
        }
        synchronized (this) {
            return original.call(uuid);
        }
    }

    // Schedules a block tick under the world lock.
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

    // Schedules a block update under the world lock.
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

    // Checks for a pending block tick under the world lock.
    @WrapMethod(method = "isBlockTickPending")
    private boolean impetus$lockedIsBlockTickPending(BlockPos pos, Block block, Operation<Boolean> original) {
        if (!this.impetus$isParallel()) {
            return original.call(pos, block);
        }
        synchronized (this) {
            return original.call(pos, block);
        }
    }

    // Checks for a scheduled update under the world lock.
    @WrapMethod(method = "isUpdateScheduled")
    private boolean impetus$lockedIsUpdateScheduled(BlockPos pos, Block block, Operation<Boolean> original) {
        if (!this.impetus$isParallel()) {
            return original.call(pos, block);
        }
        synchronized (this) {
            return original.call(pos, block);
        }
    }

    // Runs pending ticks under the world lock.
    @WrapMethod(method = "tickUpdates")
    private boolean impetus$lockedTickUpdates(boolean runAllPending, Operation<Boolean> original) {
        if (!this.impetus$isParallel()) {
            return original.call(runAllPending);
        }
        synchronized (this) {
            return original.call(runAllPending);
        }
    }

    // Adds a block event under the world lock.
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

    // Sends queued block events under the world lock.
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

    // Creates an explosion under the world lock.
    @WrapMethod(method = "newExplosion")
    private Explosion impetus$lockedNewExplosion(Entity source, double x, double y, double z, float strength, boolean causesFire, boolean damagesTerrain, Operation<Explosion> original) {
        if (!this.impetus$isParallel()) {
            return original.call(source, x, y, z, strength, causesFire, damagesTerrain);
        }
        synchronized (this) {
            return original.call(source, x, y, z, strength, causesFire, damagesTerrain);
        }
    }

    // Wraps the call of Block.randomTick in updateBlocks.
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
        batch.add(() -> block.randomTick(world, pos, state, impetus$randomTickRandom.get()));
    }

    // Runs at the start of updateBlocks.
    @Inject(method = "updateBlocks", at = @At("HEAD"))
    private void impetus$discardStaleRandomTicks(CallbackInfo ci) {
        this.impetus$randomTickBatch = null;
    }

    // Runs at the end of updateBlocks.
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
