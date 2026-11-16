package com.bdmajora.equilibrium.common.world;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;

import javax.annotation.Nullable;

// World that finds block entities without creating them.
public interface TileEntityAccess {
    // Returns the block entity at the position or null.
    @Nullable
    TileEntity equilibrium$getExistingTileEntity(BlockPos pos);
}
