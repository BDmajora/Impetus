package git.jbredwards.fluidlogged_api.api.util;

import net.minecraft.block.BlockLiquid;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.fluids.IFluidBlock;

// Test stand-in for Fluidlogged API.
public final class FluidloggedUtils {
    private FluidloggedUtils() {}

    // Returns the block at the position.
    public static IBlockState getFluidOrReal(IBlockAccess access, BlockPos pos) {
        return access.getBlockState(pos);
    }

    // Returns the fluid state through the chunkless path.
    public static FluidState getFluidState(IBlockAccess world, BlockPos pos) {
        return getFluidState(world, pos, world.getBlockState(pos));
    }

    // Returns the chunkless fluid state.
    public static FluidState getFluidState(IBlockAccess world, BlockPos pos, IBlockState state) {
        return isFluid(state) ? FluidState.of(state) : FluidState.get(world, pos);
    }

    public static IBlockState getFluidOrReal(IBlockAccess world, BlockPos pos, IBlockState state) {
        if (isFluid(state)) {
            return state;
        }
        FluidState fluid = FluidState.get(world, pos);
        return fluid.isEmpty() ? state : fluid.getState();
    }

    public static boolean isFluid(IBlockState state) {
        return state.getBlock() instanceof BlockLiquid || state.getBlock() instanceof IFluidBlock;
    }
}
