package git.jbredwards.fluidlogged_api.api.util;

import net.minecraft.block.BlockLiquid;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import git.jbredwards.fluidlogged_api.api.world.IFluidStateProvider;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.common.capabilities.ICapabilityProvider;

// Test stand-in for Fluidlogged API's fluid state.
public class FluidState {
    public static final FluidState EMPTY = new FluidState(null);

    // Answer of the next getFromProvider call.
    public static FluidState next = EMPTY;

    private final IBlockState state;

    public FluidState(IBlockState state) {
        this.state = state;
    }

    public static FluidState getFromProvider(ICapabilityProvider provider, BlockPos pos) {
        return next;
    }

    // Returns the stored fluid state at the position.
    public static FluidState get(IBlockAccess world, BlockPos pos) {
        return world instanceof IFluidStateProvider ? ((IFluidStateProvider) world).getFluidState(pos.getX(), pos.getY(), pos.getZ()) : EMPTY;
    }

    // Returns the fluid of a block state or none.
    public static FluidState of(IBlockState state) {
        return state != null && FluidloggedUtils.isFluid(state) ? new FluidState(state) : EMPTY;
    }

    public boolean isEmpty() {
        return this.state == null;
    }

    public IBlockState getState() {
        return this.state == null ? Blocks.AIR.getDefaultState() : this.state;
    }

    // Returns whether the state is a fluid.
    public boolean isValid() {
        return this.state != null && this.state.getBlock() instanceof BlockLiquid;
    }

    // Returns the liquid's level.
    public int getLevel() {
        return this.state.getValue(BlockLiquid.LEVEL);
    }

    // A vanilla fluid at level zero
    public boolean isSource() {
        return this.state != null && this.state.getBlock() instanceof BlockLiquid && this.state.getValue(BlockLiquid.LEVEL) == 0;
    }

    public FluidState toSource() {
        return this.state == null ? this : new FluidState(this.state.getBlock().getDefaultState());
    }
}
