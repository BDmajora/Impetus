package git.jbredwards.fluidlogged_api.api.util;

import net.minecraft.block.BlockLiquid;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.fluids.IFluidBlock;

// Test stand-in for Fluidlogged API, whose published jar is obfuscated and so cannot call into a deobfuscated world
public final class FluidloggedUtils {
    private FluidloggedUtils() {}

    // The fluid sharing the position, which here is simply whatever block is there
    public static IBlockState getFluidOrReal(IBlockAccess access, BlockPos pos) {
        return access.getBlockState(pos);
    }

    // Always the chunkless path here, since the stand-in world holds no chunks of fluid data
    public static FluidState getFluidState(IBlockAccess world, BlockPos pos) {
        return getFluidState(world, pos, world.getBlockState(pos));
    }

    // The chunkless fallbacks, as the real ones: a fluid block is its own fluid, anything else asks the access
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
