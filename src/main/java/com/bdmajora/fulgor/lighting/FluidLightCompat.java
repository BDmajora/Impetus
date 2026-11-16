package com.bdmajora.fulgor.lighting;

import git.jbredwards.fluidlogged_api.api.util.FluidState;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.chunk.Chunk;

// Reader of the light of fluid capabilities.
final class FluidLightCompat {
    // Prevents instantiation
    private FluidLightCompat() {
    }

    // Returns the larger of the block and fluid opacity.
    static int getLightOpacity(IBlockState state, IBlockAccess world, BlockPos pos, Chunk chunk) {
        FluidState fluid = FluidState.getFromProvider(chunk, pos);

        if (fluid.isEmpty()) {
            return state.getLightOpacity(world, pos);
        }

        return Math.max(fluid.getState().getLightOpacity(world, pos), state.getLightOpacity(world, pos));
    }

    // Returns the larger of the block and fluid light.
    static int getLightValue(int blockLightValue, IBlockAccess world, BlockPos pos, Chunk chunk) {
        FluidState fluid = FluidState.getFromProvider(chunk, pos);

        if (fluid.isEmpty()) {
            return blockLightValue;
        }

        return Math.max(fluid.getState().getLightValue(world, pos), blockLightValue);
    }
}
