package git.jbredwards.fluidlogged_api.api.capability;

import git.jbredwards.fluidlogged_api.api.util.FluidState;

// Test stand-in for the per section fluid table.
public interface IFluidStateContainer {
    FluidState getFluidState(int x, int y, int z, FluidState fallback);

    // Visits every stored position.
    default void forEach(ContainerAction action) {}

    default int deserializeX(char pos) {
        return pos & 15;
    }

    default int deserializeY(char pos) {
        return pos >> 8;
    }

    default int deserializeZ(char pos) {
        return (pos >> 4) & 15;
    }

    interface ContainerAction {
        void accept(char pos, FluidState fluidState);
    }
}
