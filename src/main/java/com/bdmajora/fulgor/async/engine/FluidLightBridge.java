package com.bdmajora.fulgor.async.engine;

import com.bdmajora.fulgor.Fulgor;
import git.jbredwards.fluidlogged_api.api.capability.IFluidStateCapability;
import git.jbredwards.fluidlogged_api.api.util.FluidState;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.chunk.Chunk;

// Folds Fluidlogged API's second state per position into the packed light info (max of both, as the mod patches into vanilla); the fluid lives in a per-chunk capability the state cache cannot see, and its classes are only touched behind Fulgor.hasFluidloggedApi()
public final class FluidLightBridge {
    private FluidLightBridge() {
    }

    // The chunk's fluid capability as an opaque handle, or null
    public static Object capabilityOf(Chunk chunk) {
        if (!Fulgor.hasFluidloggedApi() || chunk == null) {
            return null;
        }
        return IFluidStateCapability.get(chunk);
    }

    // Maxes the fluid's opacity and emission into the info; the fluid fills the block space, so the cell becomes a plain uniform absorber with no face bits
    public static int merge(int info, Object capability, int x, int y, int z, IBlockAccess access, BlockPos.MutableBlockPos lookupPos) {
        FluidState fluid = ((IFluidStateCapability) capability).getContainer(y).getFluidState(x, y, z, FluidState.EMPTY);
        if (fluid.isEmpty()) {
            return info;
        }
        IBlockState fluidState = fluid.getState();
        int fluidInfo = LightInfo.resolveContextual(LightInfo.of(fluidState), fluidState, access, lookupPos, x, y, z);
        int opacity = Math.max(info & LightInfo.OPACITY_MASK, fluidInfo & LightInfo.OPACITY_MASK);
        int emission = Math.max(LightInfo.emission(info), LightInfo.emission(fluidInfo));
        return LightInfo.COMPUTED | opacity | (emission << LightInfo.EMISSION_SHIFT);
    }

    // Scalar opacity with the fluid layer maxed in, for the heightmap walks in the chunk mixin
    public static int maxOpacityAt(Chunk chunk, int blockOpacity, int x, int y, int z, BlockPos.MutableBlockPos lookupPos) {
        IFluidStateCapability cap = IFluidStateCapability.get(chunk);
        if (cap == null) {
            return blockOpacity;
        }
        FluidState fluid = cap.getContainer(y).getFluidState(x, y, z, FluidState.EMPTY);
        if (fluid.isEmpty()) {
            return blockOpacity;
        }
        IBlockState fluidState = fluid.getState();
        int fluidInfo = LightInfo.resolveContextual(LightInfo.of(fluidState), fluidState, chunk.getWorld(), lookupPos,
                (chunk.x << 4) + x, y, (chunk.z << 4) + z);
        return Math.max(blockOpacity, LightInfo.opacity(fluidInfo));
    }
}
