package com.bdmajora.fulgor.async.engine;

import com.bdmajora.fulgor.Fulgor;
import git.jbredwards.fluidlogged_api.api.capability.IFluidStateCapability;
import git.jbredwards.fluidlogged_api.api.util.FluidState;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.chunk.Chunk;

// Bridge that adds fluid light to block light.
public final class FluidLightBridge {
    // Prevents instantiation
    private FluidLightBridge() {
    }

    // Returns the chunk's Fluidlogged API fluid capability or null.
    public static Object capabilityOf(Chunk chunk) {
        if (!Fulgor.hasFluidloggedApi() || chunk == null) {
            return null;
        }
        return IFluidStateCapability.get(chunk);
    }

    // Returns the light info joined with the light of a fluid capability.
    public static int merge(int info, Object capability, int x, int y, int z, IBlockAccess access, BlockPos.MutableBlockPos lookupPos) {
        FluidState fluid = ((IFluidStateCapability) capability).getContainer(y).getFluidState(x, y, z, FluidState.EMPTY);
        if (fluid.isEmpty()) {
            return info;
        }
        IBlockState fluidState = fluid.getState();
        int fluidInfo = LightInfo.resolveContextual(LightInfo.of(fluidState), fluidState, access, lookupPos, x, y, z);
        int opacity = Math.max(info & LightInfo.OPACITY_MASK, fluidInfo & LightInfo.OPACITY_MASK);
        int emission = Math.max(LightInfo.emission(info), LightInfo.emission(fluidInfo));
        // Returns the light info with the fluid's opacity and emission.
        return LightInfo.withLightValues(info, opacity, emission);
    }

    // Returns the larger of the block and fluid opacity.
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
