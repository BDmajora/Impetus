package com.bdmajora.coarctatio.mixin.events;

import com.bdmajora.coarctatio.events.RecyclableEvent;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

// The side is the one field a recycled player tick event has to change between posts; the phase lives here because Event's own is private and Event is loaded before mixins apply. remap = false, Forge class
@Mixin(value = TickEvent.class, remap = false)
public abstract class TickEventMixin extends Event implements RecyclableEvent {
    @Shadow
    @Final
    @Mutable
    public Side side;

    @Unique
    private EventPriority coarctatio$phase;

    @Nullable
    @Override
    public EventPriority getPhase() {
        return this.coarctatio$phase;
    }

    // Vanilla's ordering check, against the recycled phase
    @Override
    public void setPhase(@Nonnull EventPriority value) {
        this.coarctatio$phase = RecyclableEvent.advancePhase(this.coarctatio$phase, value);
    }

    @Override
    public void coarctatio$resetPhase() {
        this.coarctatio$phase = null;
    }

    @Override
    public void coarctatio$refreshSide(Side side) {
        this.side = side;
    }
}
