package com.bdmajora.dynamiclights.client;

import git.jbredwards.fluidlogged_api.api.util.FluidloggedUtils;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

// Bridge to the fluid logging mod.
final class FluidloggedCompat {
    // Prevents instantiation
    private FluidloggedCompat() {
    }

    // Returns the fluid state or the block state of the position.
    static IBlockState getFluidOrReal(IBlockAccess access, BlockPos pos) {
        return FluidloggedUtils.getFluidOrReal(access, pos);
    }
}
