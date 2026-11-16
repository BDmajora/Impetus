package com.bdmajora.coarctatio.events;

import com.google.common.base.Preconditions;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.relauncher.Side;

import java.util.EnumSet;

// Event that can be reset and reused.
public interface RecyclableEvent {
    // Restores the cancel state and result to their defaults.
    default void coarctatio$resetEventState() {
        coarctatio$resetPhase();
        Event self = (Event) this;
        if (self.isCancelable()) {
            self.setCanceled(false);
        }
        if (self.hasResult()) {
            self.setResult(Event.Result.DEFAULT);
        }
    }

    // Restores the phase to the unset state.
    void coarctatio$resetPhase();

    // Returns the next phase and rejects a null or earlier phase.
    static EventPriority advancePhase(EventPriority current, EventPriority value) {
        Preconditions.checkNotNull(value, "setPhase argument must not be null");
        int prev = current == null ? -1 : current.ordinal();
        Preconditions.checkArgument(prev < value.ordinal(), "Attempted to set event phase to %s when already %s", value, current);
        return value;
    }

    // Stores the side of the event.
    default void coarctatio$refreshSide(Side side) {
    }

    // Stores the player of the event.
    default void coarctatio$refreshPlayer(EntityPlayer player) {
    }

    // Stores the world of the event.
    default void coarctatio$refreshWorld(World world) {
    }

    // Stores the render time of the event.
    default void coarctatio$refreshRenderTime(float renderTickTime) {
    }

    // Stores the object of the event.
    default void coarctatio$refreshObject(Object object) {
    }

    // Stores the block data of the event.
    default void coarctatio$refreshBlock(World world, BlockPos pos, IBlockState state) {
    }

    // Stores the notified sides of the event.
    default void coarctatio$refreshNeighbors(EnumSet<EnumFacing> notifiedSides, boolean forceRedstoneUpdate) {
    }
}
