package git.jbredwards.fluidlogged_api.api.world;

import git.jbredwards.fluidlogged_api.api.util.FluidState;
import net.minecraft.util.math.Vec3i;

// Test stand-in: the published interface's default method calls Vec3i by its obfuscated getter names
public interface IFluidStateProvider {
    FluidState getFluidState(int x, int y, int z);

    default FluidState getFluidState(Vec3i pos) {
        return getFluidState(pos.getX(), pos.getY(), pos.getZ());
    }
}
