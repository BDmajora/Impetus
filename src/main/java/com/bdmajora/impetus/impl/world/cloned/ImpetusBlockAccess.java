package com.bdmajora.impetus.impl.world.cloned;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.biome.BiomeColorHelper;
import com.bdmajora.impetus.impl.compat.fluidlogged.FluidloggedBlockAccess;

// Contract of the impetus block access.
public interface ImpetusBlockAccess extends IBlockAccess, FluidloggedBlockAccess {
    // Returns the block tint.
    int getBlockTint(BlockPos pos, BiomeColorHelper.ColorResolver resolver);

    // Returns the block state.
    IBlockState getBlockState(int x, int y, int z);
}
