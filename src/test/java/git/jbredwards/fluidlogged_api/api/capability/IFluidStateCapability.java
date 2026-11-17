package git.jbredwards.fluidlogged_api.api.capability;

import net.minecraftforge.common.capabilities.ICapabilityProvider;

// Test stand-in for the chunk capability holding a world's fluidlogged states
public interface IFluidStateCapability {
    // Answer of the next get call.
    IFluidStateCapability[] next = new IFluidStateCapability[1];

    static IFluidStateCapability get(ICapabilityProvider provider) {
        return next[0];
    }

    IFluidStateContainer getContainer(int y);
}
