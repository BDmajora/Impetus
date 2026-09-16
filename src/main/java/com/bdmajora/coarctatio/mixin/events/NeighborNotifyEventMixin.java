package com.bdmajora.coarctatio.mixin.events;

import com.bdmajora.coarctatio.events.RecyclableEvent;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.event.world.BlockEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;

import java.util.EnumSet;

// remap = false, Forge class
@Mixin(value = BlockEvent.NeighborNotifyEvent.class, remap = false)
public abstract class NeighborNotifyEventMixin implements RecyclableEvent {
    @Shadow
    @Final
    @Mutable
    private EnumSet<EnumFacing> notifiedSides;

    @Shadow
    @Final
    @Mutable
    private boolean forceRedstoneUpdate;

    @Override
    public void coarctatio$refreshNeighbors(EnumSet<EnumFacing> notifiedSides, boolean forceRedstoneUpdate) {
        this.notifiedSides = notifiedSides;
        this.forceRedstoneUpdate = forceRedstoneUpdate;
    }
}
