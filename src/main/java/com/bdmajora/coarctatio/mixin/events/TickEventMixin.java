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

// Mixin that changes TickEvent.
@Mixin(value = TickEvent.class, remap = false)
public abstract class TickEventMixin extends Event implements RecyclableEvent {
    // Reference to the field side of the target.
    @Shadow
    @Final
    @Mutable
    public Side side;

    // Field phase added to the target.
    @Unique
    private EventPriority coarctatio$phase;

    // Returns the phase.
    @Nullable
    @Override
    public EventPriority getPhase() {
        return this.coarctatio$phase;
    }

    // Handles set phase.
    @Override
    public void setPhase(@Nonnull EventPriority value) {
        this.coarctatio$phase = RecyclableEvent.advancePhase(this.coarctatio$phase, value);
    }

    // Handles reset phase.
    @Override
    public void coarctatio$resetPhase() {
        this.coarctatio$phase = null;
    }

    // Handles refresh side.
    @Override
    public void coarctatio$refreshSide(Side side) {
        this.side = side;
    }
}
