package com.bdmajora.coarctatio.mixin.events;

import com.bdmajora.coarctatio.events.RecyclableEvent;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.GenericEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;

// Mixin that changes AttachCapabilitiesEvent.
@Mixin(value = AttachCapabilitiesEvent.class, remap = false)
public abstract class AttachCapabilitiesEventMixin<T> extends GenericEvent<T> implements RecyclableEvent {
    // Creates an attach capabilities event mixin from its type.
    private AttachCapabilitiesEventMixin(Class<T> type) {
        super(type);
        throw new AssertionError();
    }

    // Reference to the field obj of the target.
    @Shadow
    @Final
    @Mutable
    private T obj;

    @Shadow
    @Final
    private Map<ResourceLocation, ICapabilityProvider> caps;

    // Priority phase the reused event is in.
    @Unique
    private EventPriority coarctatio$phase;

    // Returns the phase.
    @Nullable
    @Override
    public EventPriority getPhase() {
        return this.coarctatio$phase;
    }

    // Advances the phase and refuses to go back.
    @Override
    public void setPhase(@Nonnull EventPriority value) {
        this.coarctatio$phase = RecyclableEvent.advancePhase(this.coarctatio$phase, value);
    }

    // Clears the phase before the event is posted again.
    @Override
    public void coarctatio$resetPhase() {
        this.coarctatio$phase = null;
    }

    // Points the reused event at a new object and clears the capabilities of the last one.
    @Override
    @SuppressWarnings("unchecked")
    public void coarctatio$refreshObject(Object object) {
        this.obj = (T) object;
        this.caps.clear();
    }
}
