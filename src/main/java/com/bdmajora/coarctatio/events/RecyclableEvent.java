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

// Implemented by mixin on each recycled event class (never on Event itself, which Forge loads before mixins can touch it) so an instance can be put back to freshly constructed state before it is posted again
public interface RecyclableEvent {
    // Clears the cancel flag, result and dispatch phase Forge's bus wrote during the last post
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

    // Forgets the dispatch phase; Event's own field is private, so each recycled class keeps a replacement and overrides getPhase/setPhase onto it
    void coarctatio$resetPhase();

    // Event.setPhase's contract, phases only ever advance, applied to the replacement field
    static EventPriority advancePhase(EventPriority current, EventPriority value) {
        Preconditions.checkNotNull(value, "setPhase argument must not be null");
        int prev = current == null ? -1 : current.ordinal();
        Preconditions.checkArgument(prev < value.ordinal(), "Attempted to set event phase to %s when already %s", value, current);
        return value;
    }

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
