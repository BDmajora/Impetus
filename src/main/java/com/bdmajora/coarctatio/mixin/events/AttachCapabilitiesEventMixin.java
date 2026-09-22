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

// The object changes per post and the providers gathered last time are cleared; the dispatcher copied them out before the event was released. remap = false, Forge class
@Mixin(value = AttachCapabilitiesEvent.class, remap = false)
public abstract class AttachCapabilitiesEventMixin<T> extends GenericEvent<T> implements RecyclableEvent {
    // Never invoked; a mixin extending its target's superclass has to satisfy the constructor
    private AttachCapabilitiesEventMixin(Class<T> type) {
        super(type);
        throw new AssertionError();
    }

    @Shadow
    @Final
    @Mutable
    private T obj;

    @Shadow
    @Final
    private Map<ResourceLocation, ICapabilityProvider> caps;

    @Unique
    private EventPriority coarctatio$phase;

    @Nullable
    @Override
    public EventPriority getPhase() {
        return this.coarctatio$phase;
    }

    @Override
    public void setPhase(@Nonnull EventPriority value) {
        this.coarctatio$phase = RecyclableEvent.advancePhase(this.coarctatio$phase, value);
    }

    @Override
    public void coarctatio$resetPhase() {
        this.coarctatio$phase = null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void coarctatio$refreshObject(Object object) {
        this.obj = (T) object;
        this.caps.clear();
    }
}
