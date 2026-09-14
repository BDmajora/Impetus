package com.bdmajora.equilibrium.mixin.worldgen.chunk_copy;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkPrimer;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

// Copying a primer into a new chunk visits every slot once into sections that start empty, so the state being replaced is always air: the redirect keeps the counter bookkeeping and the palette write and drops the read-back of a slot known to be empty (Noisium's direct section write, on 1.12.2's storage)
@Mixin(Chunk.class)
public abstract class ChunkMixin {
    @Redirect(method = "<init>(Lnet/minecraft/world/World;Lnet/minecraft/world/chunk/ChunkPrimer;II)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/chunk/storage/ExtendedBlockStorage;set(IIILnet/minecraft/block/state/IBlockState;)V"))
    private void equilibrium$fillFreshSlot(ExtendedBlockStorage storage, int x, int y, int z, IBlockState state) {
        Block block = state.getBlock();
        if (block != Blocks.AIR) {
            ExtendedBlockStorageAccessor counts = (ExtendedBlockStorageAccessor) storage;
            counts.equilibrium$setBlockRefCount(counts.equilibrium$getBlockRefCount() + 1);
            if (block.getTickRandomly()) {
                counts.equilibrium$setTickRefCount(counts.equilibrium$getTickRefCount() + 1);
            }
        }
        storage.getData().set(x, y, z, state);
    }
}
