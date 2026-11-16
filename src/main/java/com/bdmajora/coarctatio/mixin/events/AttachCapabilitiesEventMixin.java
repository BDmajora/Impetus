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

    // Handles refresh object.
    @Override
    @SuppressWarnings("unchecked")
    public void coarctatio$refreshObject(Object object) {
        this.obj = (T) object;
        this.caps.clear();
    }
}
