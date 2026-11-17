package com.bdmajora.coarctatio.mixin.events;

import com.bdmajora.coarctatio.events.RecyclableEvent;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.event.world.BlockEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;

import java.util.EnumSet;

// Mixin that changes NeighborNotifyEvent.
@Mixin(value = BlockEvent.NeighborNotifyEvent.class, remap = false)
public abstract class NeighborNotifyEventMixin implements RecyclableEvent {
    // Reference to the field notified sides of the target.
    @Shadow
    @Final
    @Mutable
    private EnumSet<EnumFacing> notifiedSides;

    // Reference to the field force redstone update of the target.
    @Shadow
    @Final
    @Mutable
    private boolean forceRedstoneUpdate;

    // Points the reused event at new sides and redstone flag.
    @Override
    public void coarctatio$refreshNeighbors(EnumSet<EnumFacing> notifiedSides, boolean forceRedstoneUpdate) {
        this.notifiedSides = notifiedSides;
        this.forceRedstoneUpdate = forceRedstoneUpdate;
    }
}
