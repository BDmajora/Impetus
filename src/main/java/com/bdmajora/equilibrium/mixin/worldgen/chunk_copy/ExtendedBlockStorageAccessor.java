package com.bdmajora.equilibrium.mixin.worldgen.chunk_copy;

import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// Mixin that changes ExtendedBlockStorage.
@Mixin(ExtendedBlockStorage.class)
public interface ExtendedBlockStorageAccessor {
    // Gives access to the field get block ref count.
    @Accessor("blockRefCount")
    int equilibrium$getBlockRefCount();

    // Gives access to the field set block ref count.
    @Accessor("blockRefCount")
    void equilibrium$setBlockRefCount(int count);

    // Gives access to the field get tick ref count.
    @Accessor("tickRefCount")
    int equilibrium$getTickRefCount();

    // Gives access to the field set tick ref count.
    @Accessor("tickRefCount")
    void equilibrium$setTickRefCount(int count);
}
