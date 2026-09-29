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

    public static boolean isFluid(IBlockState state) {
        return state.getBlock() instanceof BlockLiquid || state.getBlock() instanceof IFluidBlock;
    }

    // Anything short of a full cube has room for a fluid
    public static boolean isStateFluidloggable(IBlockState state, IBlockAccess world, BlockPos pos, FluidState fluid) {
        return !state.isFullCube();
    }
}
