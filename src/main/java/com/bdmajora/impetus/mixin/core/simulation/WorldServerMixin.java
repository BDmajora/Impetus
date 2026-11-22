package com.bdmajora.impetus.mixin.core.simulation;

import com.bdmajora.impetus.impl.world.SimulationDistance;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.NextTickListEntry;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.TreeSet;

@Mixin(WorldServer.class)
public abstract class WorldServerMixin {
    @Unique
    private static final ExtendedBlockStorage[] impetus$NO_RANDOM_TICK_SECTIONS = new ExtendedBlockStorage[0];

    @WrapOperation(method = "updateBlocks", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/chunk/Chunk;getBlockStorageArray()[Lnet/minecraft/world/chunk/storage/ExtendedBlockStorage;"))
    private ExtendedBlockStorage[] impetus$limitRandomTicks(Chunk chunk, Operation<ExtendedBlockStorage[]> original) {
        return SimulationDistance.shouldTick((WorldServer) (Object) this, chunk.x, chunk.z)
                ? original.call(chunk) : impetus$NO_RANDOM_TICK_SECTIONS;
    }

    @WrapOperation(method = "updateBlocks", at = {
            @At(value = "INVOKE", target = "Lnet/minecraft/world/WorldProvider;canDoLightning(Lnet/minecraft/world/chunk/Chunk;)Z"),
            @At(value = "INVOKE", target = "Lnet/minecraft/world/WorldProvider;canDoRainSnowIce(Lnet/minecraft/world/chunk/Chunk;)Z")
    })
    private boolean impetus$limitWeatherTicks(WorldProvider provider, Chunk chunk, Operation<Boolean> original) {
        return SimulationDistance.shouldTick((WorldServer) (Object) this, chunk.x, chunk.z)
                && original.call(provider, chunk);
    }

    // Returned once no due, in-range entry is left: its far-future time trips vanilla's "not due yet" break.
    @Unique
    private static final NextTickListEntry impetus$NO_DUE_TICK = new NextTickListEntry(BlockPos.ORIGIN, null).setScheduledTime(Long.MAX_VALUE);

    // Hands vanilla's cleaning loop the next due entry that is inside the simulation distance, stepping past the others with a
    // cursor that lives for this call. Skipped entries stay in both sets untouched, so they keep their time and priority, are still
    // saved with their chunk, and never use up the 65536-per-tick budget; taking them out and putting them back every tick would
    // let a backlog of out-of-range ticks (always the oldest, so always first) crowd out every in-range one.
    @WrapOperation(method = "tickUpdates", at = @At(value = "INVOKE", target = "Ljava/util/TreeSet;first()Ljava/lang/Object;", remap = false))
    private Object impetus$nextSimulatedTick(TreeSet<NextTickListEntry> pending, Operation<Object> original,
                                             @Local(argsOnly = true) boolean runAllPending,
                                             @Share("tickCursor") LocalRef<NextTickListEntry> cursor) {
        if (runAllPending) {
            return original.call(pending);
        }
        WorldServer world = (WorldServer) (Object) this;
        long now = world.getTotalWorldTime();
        NextTickListEntry skipped = cursor.get();
        NextTickListEntry entry = skipped == null ? (NextTickListEntry) original.call(pending) : pending.higher(skipped);
        while (entry != null && entry.scheduledTime <= now && !SimulationDistance.shouldTick(world, entry.position)) {
            cursor.set(entry);
            entry = pending.higher(entry);
        }
        return entry == null ? impetus$NO_DUE_TICK : entry;
    }
}
