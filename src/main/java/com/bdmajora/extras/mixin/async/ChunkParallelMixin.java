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

// Mixin that changes Chunk.
@Mixin(Chunk.class)
public abstract class ChunkParallelMixin {
    @Shadow
    @Final
    @Mutable
    private Map<BlockPos, TileEntity> tileEntities;

    // Reference to the field world of the target.
    @Shadow
    @Final
    private World world;

    // Lock of the chunk's entity lists.
    @Unique
    private final Object impetus$entityLock = new Object();

    // Whether the chunk belongs to a parallel ticked world.
    @Unique
    private boolean impetus$parallel;

    // Reference to the method get block storage array of the target.
    @Shadow
    public abstract ExtendedBlockStorage[] getBlockStorageArray();

    // Runs before every return of constructor.
    @Inject(method = "<init>(Lnet/minecraft/world/World;II)V", at = @At("RETURN"))
    private void impetus$installConcurrentMap(World world, int x, int z, CallbackInfo ci) {
        if (!ParallelProcessor.INSTALLED || world == null || world.isRemote || !((ParallelWorld) world).impetus$isParallel()) {
            return;
        }
        this.impetus$parallel = true;
        this.tileEntities = new ConcurrentHashMap<>(this.tileEntities);
    }

    // Runs before every return of constructor.
    @Inject(method = "<init>(Lnet/minecraft/world/World;Lnet/minecraft/world/chunk/ChunkPrimer;II)V", at = @At("RETURN"))
    private void impetus$lockGeneratedSections(World world, ChunkPrimer primer, int x, int z, CallbackInfo ci) {
        this.impetus$lockSections();
    }

    // Runs before every return of setStorageArrays.
    @Inject(method = "setStorageArrays", at = @At("RETURN"))
    private void impetus$lockLoadedSections(ExtendedBlockStorage[] sections, CallbackInfo ci) {
        this.impetus$lockSections();
    }

    // Turns on the parallel lock of a new section in a parallel chunk.
    @WrapOperation(method = {"setBlockState", "setLightFor"}, at = @At(value = "NEW", target = "(IZ)Lnet/minecraft/world/chunk/storage/ExtendedBlockStorage;", remap = false))
    private ExtendedBlockStorage impetus$lockNewSection(int y, boolean storeSkylight, Operation<ExtendedBlockStorage> original) {
        ExtendedBlockStorage section = original.call(y, storeSkylight);
        if (this.impetus$parallel) {
            ((ParallelBlockStateContainer) section.getData()).impetus$enableParallelLock();
        }
        return section;
    }

    // Turns on the parallel lock of every section of a parallel chunk.
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

    // Wraps the call of ClassInheritanceMultiMap.add in addEntity.
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

    // Wraps the call of ClassInheritanceMultiMap.remove in removeEntityAtIndex.
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

    // Collects entities in a box under the chunk's entity lock.
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

    // Collects entities of a type in a box under the chunk's entity lock.
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

    // Returns a live block entity without locking or creates it under the world lock.
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
