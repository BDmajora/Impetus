package com.bdmajora.fulgor.api;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EnumSkyBlock;

// Contract of the chunk lighting data.
public interface ChunkLightingData {
    // Returns the neighbor light checks.
    short[] fulgor$getNeighborLightChecks();

    // Stores the neighbor light checks.
    void fulgor$setNeighborLightChecks(short[] data);

    // Initializes the neighbor light checks.
    void fulgor$initNeighborLightChecks();

    // Returns whether the chunk's light was computed once.
    boolean fulgor$isLightInitialized();

    // Stores the light initialized.
    void fulgor$setLightInitialized(boolean lightInitialized);

    // Stores the skylight updated.
    void fulgor$setSkylightUpdated();

    // Returns the cached light for.
    int fulgor$getCachedLightFor(EnumSkyBlock lightType, BlockPos pos);
}
