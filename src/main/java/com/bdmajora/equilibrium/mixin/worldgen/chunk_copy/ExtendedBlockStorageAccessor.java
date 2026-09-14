package com.bdmajora.equilibrium.mixin.worldgen.chunk_copy;

import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// The two counters ExtendedBlockStorage.set maintains, so a fresh section can be filled without re-reading every slot it just wrote
@Mixin(ExtendedBlockStorage.class)
public interface ExtendedBlockStorageAccessor {
    @Accessor("blockRefCount")
    int equilibrium$getBlockRefCount();

    @Accessor("blockRefCount")
    void equilibrium$setBlockRefCount(int count);

    @Accessor("tickRefCount")
    int equilibrium$getTickRefCount();

    @Accessor("tickRefCount")
    void equilibrium$setTickRefCount(int count);
}
