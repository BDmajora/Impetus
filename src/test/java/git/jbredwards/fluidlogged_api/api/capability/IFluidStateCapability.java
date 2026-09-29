package git.jbredwards.fluidlogged_api.api.capability;

import net.minecraftforge.common.capabilities.ICapabilityProvider;

// Test stand-in for the chunk capability holding a world's fluidlogged states
public interface IFluidStateCapability {
    // What the next get() answers with, so a test can decide whether a chunk carries fluid data
    IFluidStateCapability[] next = new IFluidStateCapability[1];

    static IFluidStateCapability get(ICapabilityProvider provider) {
        return next[0];
    }

    IFluidStateContainer getContainer(int y);
}
