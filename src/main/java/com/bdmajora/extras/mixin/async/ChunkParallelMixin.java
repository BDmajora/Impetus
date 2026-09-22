package com.bdmajora.extras.mixin.async;

import com.bdmajora.extras.async.ParallelBlockStateContainer;
import com.bdmajora.extras.async.ParallelProcessor;
import com.bdmajora.extras.async.ParallelWorld;
import com.google.common.base.Predicate;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ClassInheritanceMultiMap;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkPrimer;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Per-chunk shared state: the section entity lists get their own lock (never the chunk monitor, since block-entity creation below takes the world monitor and the two orders would cross), and the block-entity map becomes concurrent so off-thread lookups need no lock at all
@Mixin(Chunk.class)
public abstract class ChunkParallelMixin {
    @Shadow
    @Final
    @Mutable
    private Map<BlockPos, TileEntity> tileEntities;

    @Shadow
    @Final
    private World world;

    // Guards entityLists; a dedicated object so the lock order is always world monitor -> entity lock and never the reverse
    @Unique
    private final Object impetus$entityLock = new Object();

    @Unique
    private boolean impetus$parallel;

    @Shadow
    public abstract ExtendedBlockStorage[] getBlockStorageArray();

    @Inject(method = "<init>(Lnet/minecraft/world/World;II)V", at = @At("RETURN"))
    private void impetus$installConcurrentMap(World world, int x, int z, CallbackInfo ci) {
        if (!ParallelProcessor.INSTALLED || world == null || world.isRemote || !((ParallelWorld) world).impetus$isParallel()) {
            return;
        }
        this.impetus$parallel = true;
        this.tileEntities = new ConcurrentHashMap<>(this.tileEntities);
    }

    // The sections a parallel world's chunks carry get their palette lock here, the places a section comes into being: the generator's primer copy, the loader's setStorageArrays, and setBlockState or setLightFor filling an empty one. The section has no world of its own to ask (see ParallelBlockStateContainer)
    @Inject(method = "<init>(Lnet/minecraft/world/World;Lnet/minecraft/world/chunk/ChunkPrimer;II)V", at = @At("RETURN"))
    private void impetus$lockGeneratedSections(World world, ChunkPrimer primer, int x, int z, CallbackInfo ci) {
        this.impetus$lockSections();
    }

    @Inject(method = "setStorageArrays", at = @At("RETURN"))
    private void impetus$lockLoadedSections(ExtendedBlockStorage[] sections, CallbackInfo ci) {
        this.impetus$lockSections();
    }

    @WrapOperation(method = {"setBlockState", "setLightFor"}, at = @At(value = "NEW", target = "(IZ)Lnet/minecraft/world/chunk/storage/ExtendedBlockStorage;"))
    private ExtendedBlockStorage impetus$lockNewSection(int y, boolean storeSkylight, Operation<ExtendedBlockStorage> original) {
        ExtendedBlockStorage section = original.call(y, storeSkylight);
        if (this.impetus$parallel) {
            ((ParallelBlockStateContainer) section.getData()).impetus$enableParallelLock();
        }
        return section;
    }

    @Unique
    private void impetus$lockSections() {
        if (!this.impetus$parallel) {
            return;
        }
        for (ExtendedBlockStorage section : this.getBlockStorageArray()) {
            if (section != null) {
                ((ParallelBlockStateContainer) section.getData()).impetus$enableParallelLock();
            }
        }
    }

    @WrapOperation(method = "addEntity", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/util/ClassInheritanceMultiMap;add(Ljava/lang/Object;)Z"))
    private boolean impetus$lockedAdd(ClassInheritanceMultiMap<Entity> list, Object entity, Operation<Boolean> original) {
        if (!this.impetus$parallel) {
            return original.call(list, entity);
        }
        synchronized (this.impetus$entityLock) {
            return original.call(list, entity);
        }
    }

    @WrapOperation(method = "removeEntityAtIndex", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/util/ClassInheritanceMultiMap;remove(Ljava/lang/Object;)Z"))
    private boolean impetus$lockedRemove(ClassInheritanceMultiMap<Entity> list, Object entity, Operation<Boolean> original) {
        if (!this.impetus$parallel) {
            return original.call(list, entity);
        }
        synchronized (this.impetus$entityLock) {
            return original.call(list, entity);
        }
    }

    @WrapMethod(method = "getEntitiesWithinAABBForEntity")
    private void impetus$lockedGetEntities(Entity except, AxisAlignedBB box, List<Entity> out, Predicate<? super Entity> filter, Operation<Void> original) {
        if (!this.impetus$parallel) {
            original.call(except, box, out, filter);
            return;
        }
        synchronized (this.impetus$entityLock) {
            original.call(except, box, out, filter);
        }
    }

    @WrapMethod(method = "getEntitiesOfTypeWithinAABB")
    private <T extends Entity> void impetus$lockedGetEntitiesOfType(Class<? extends T> type, AxisAlignedBB box, List<T> out, Predicate<? super T> filter, Operation<Void> original) {
        if (!this.impetus$parallel) {
            original.call(type, box, out, filter);
            return;
        }
        synchronized (this.impetus$entityLock) {
            original.call(type, box, out, filter);
        }
    }

    // Lookups are lock-free on the concurrent map; only the create-on-miss path, which registers with the world, goes through the world monitor so two threads cannot both build the same block entity
    @WrapMethod(method = "getTileEntity")
    private TileEntity impetus$lockedGetTileEntity(BlockPos pos, Chunk.EnumCreateEntityType mode, Operation<TileEntity> original) {
        if (!this.impetus$parallel) {
            return original.call(pos, mode);
        }
        TileEntity existing = this.tileEntities.get(pos);
        if (existing != null && !existing.isInvalid()) {
            return existing;
        }
        if (mode == Chunk.EnumCreateEntityType.CHECK && existing == null) {
            return null;
        }
        synchronized (this.world) {
            return original.call(pos, mode);
        }
    }
}
