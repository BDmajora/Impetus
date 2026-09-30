package git.jbredwards.fluidlogged_api.api.util;

import net.minecraft.block.BlockLiquid;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import git.jbredwards.fluidlogged_api.api.world.IFluidStateProvider;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.common.capabilities.ICapabilityProvider;

// Test stand-in for Fluidlogged API's fluid state: the published jar is obfuscated and cannot call into a
// deobfuscated world, so the few members Fulgor and the chunk mesher use are provided here instead
public class FluidState {
    public static final FluidState EMPTY = new FluidState(null);

    // What the next getFromProvider call answers with, so a test can put a fluid at a position
    public static FluidState next = EMPTY;

    private final IBlockState state;

    public FluidState(IBlockState state) {
        this.state = state;
    }

    public static FluidState getFromProvider(ICapabilityProvider provider, BlockPos pos) {
        return next;
    }

    // Whatever the access stores at the position, when it stores fluid states at all
    public static FluidState get(IBlockAccess world, BlockPos pos) {
        return world instanceof IFluidStateProvider ? ((IFluidStateProvider) world).getFluidState(pos.getX(), pos.getY(), pos.getZ()) : EMPTY;
    }

    // The fluid a block state is, or none for anything that is not a fluid
    public static FluidState of(IBlockState state) {
        return state != null && FluidloggedUtils.isFluid(state) ? new FluidState(state) : EMPTY;
    }

    public boolean isEmpty() {
        return this.state == null;
    }

    public IBlockState getState() {
        return this.state == null ? Blocks.AIR.getDefaultState() : this.state;
    }

    // A fluid block, which here is any vanilla liquid
    public boolean isValid() {
        return this.state != null && this.state.getBlock() instanceof BlockLiquid;
    }

    // The liquid's level property, 8 and up meaning it falls
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
