package com.bdmajora.impetus.impl.compat.fluidlogged;

import git.jbredwards.fluidlogged_api.api.capability.IFluidStateCapability;
import git.jbredwards.fluidlogged_api.api.capability.IFluidStateContainer;
import git.jbredwards.fluidlogged_api.api.util.FluidState;
import net.minecraft.world.chunk.BlockStateContainer;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import org.apache.commons.lang3.mutable.MutableInt;

import java.util.Objects;

// Fluid states of one section.
public class FluidStateStorage {
    // Container of the fluid states.
    private final BlockStateContainer data = new BlockStateContainer();
    // Number of positions with a fluid.
    private final int nonEmptyCount;

    // Copies the fluid states of the section at a block height of a chunk.
    public FluidStateStorage(Chunk chunkIn, int yIn) {
        IFluidStateContainer container = Objects.requireNonNull(IFluidStateCapability.get((ICapabilityProvider)chunkIn)).getContainer(yIn);
        MutableInt counter = new MutableInt();
        container.forEach((pos, fluidState) -> {
            int y = container.deserializeY(pos);
            if (y >> 4 == yIn >> 4) {
                int x = container.deserializeX(pos);
                int z = container.deserializeZ(pos);

                data.set(x & 15, y & 15, z & 15, fluidState.getState());
                counter.increment();
            }
        });
        this.nonEmptyCount = counter.intValue();
    }

    // Returns whether the section has no fluid.
    public boolean isEmpty() {
        return this.nonEmptyCount == 0;
    }

    // Returns the fluid state at a local position.
    public Object get(int x, int y, int z) {
        return FluidState.of(data.get(x, y, z));
    }
}