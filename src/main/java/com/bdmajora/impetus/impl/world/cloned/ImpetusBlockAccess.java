package com.bdmajora.impetus.impl.world.cloned;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.biome.BiomeColorHelper;
import com.bdmajora.impetus.impl.compat.fluidlogged.FluidloggedBlockAccess;

// Extends vanilla IBlockAccess with biome tinting and fluidlogged block support
public interface ImpetusBlockAccess extends IBlockAccess, FluidloggedBlockAccess {
    int getBlockTint(BlockPos pos, BiomeColorHelper.ColorResolver resolver);

    // Allocation-free form of IBlockAccess#getBlockState, for callers probing many neighbours
    IBlockState getBlockState(int x, int y, int z);
}
