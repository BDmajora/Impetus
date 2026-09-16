package com.bdmajora.coarctatio.events;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;

import java.util.EnumSet;

// Implemented by mixin on each recycled event class (never on Event itself, which Forge loads before mixins can touch it) so an instance can be put back to freshly constructed state before it is posted again
public interface RecyclableEvent {
    // Clears the cancel flag, result and dispatch phase Forge's bus wrote during the last post
    void coarctatio$resetEventState();

    default void coarctatio$refreshSide(Side side) {
    }

    default void coarctatio$refreshPlayer(EntityPlayer player) {
    }

    default void coarctatio$refreshWorld(World world) {
    }

    default void coarctatio$refreshRenderTime(float renderTickTime) {
    }

    default void coarctatio$refreshObject(Object object) {
    }

    default void coarctatio$refreshBlock(World world, BlockPos pos, IBlockState state) {
    }

    default void coarctatio$refreshNeighbors(EnumSet<EnumFacing> notifiedSides, boolean forceRedstoneUpdate) {
    }
}
