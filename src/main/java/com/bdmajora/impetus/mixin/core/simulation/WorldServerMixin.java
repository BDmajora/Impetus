package com.bdmajora.impetus.mixin.core.simulation;

import com.bdmajora.impetus.impl.world.SimulationDistance;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.minecraft.world.NextTickListEntry;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

@Mixin(WorldServer.class)
public abstract class WorldServerMixin {
    @Shadow
    @Final
    private Set<NextTickListEntry> pendingTickListEntriesHashSet;

    @Shadow
    @Final
    private TreeSet<NextTickListEntry> pendingTickListEntriesTreeSet;

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

    // Hold out of range entries until this batch is selected, keeping their time and priority.
    @WrapOperation(method = "tickUpdates", at = @At(value = "INVOKE",
            target = "Ljava/util/List;add(Ljava/lang/Object;)Z", remap = false))
    private boolean impetus$deferScheduledTick(List<NextTickListEntry> batch, Object value, Operation<Boolean> original,
                                              @Local(argsOnly = true) boolean runAllPending,
                                              @Share("deferredTicks") LocalRef<List<NextTickListEntry>> deferredTicks) {
        NextTickListEntry entry = (NextTickListEntry) value;
        if (runAllPending || SimulationDistance.shouldTick((WorldServer) (Object) this, entry.position)) {
            return original.call(batch, entry);
        }
        if (deferredTicks.get() == null) {
            deferredTicks.set(new ArrayList<>());
        }
        return deferredTicks.get().add(entry);
    }

    @Inject(method = "tickUpdates", at = @At(value = "INVOKE", target = "Ljava/util/List;iterator()Ljava/util/Iterator;", remap = false))
    private void impetus$restoreDeferredTicks(boolean runAllPending, CallbackInfoReturnable<Boolean> cir,
                                             @Share("deferredTicks") LocalRef<List<NextTickListEntry>> deferredTicks) {
        List<NextTickListEntry> deferred = deferredTicks.get();
        if (deferred != null) {
            this.pendingTickListEntriesHashSet.addAll(deferred);
            this.pendingTickListEntriesTreeSet.addAll(deferred);
        }
    }
}
