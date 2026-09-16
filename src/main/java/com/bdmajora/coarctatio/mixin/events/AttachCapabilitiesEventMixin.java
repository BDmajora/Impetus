package com.bdmajora.coarctatio.mixin.events;

import com.bdmajora.coarctatio.events.RecyclableEvent;
import com.google.common.base.Preconditions;
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
        Preconditions.checkNotNull(value, "setPhase argument must not be null");
        int prev = this.coarctatio$phase == null ? -1 : this.coarctatio$phase.ordinal();
        Preconditions.checkArgument(prev < value.ordinal(), "Attempted to set event phase to %s when already %s", value, this.coarctatio$phase);
        this.coarctatio$phase = value;
    }

    @Override
    public void coarctatio$resetEventState() {
        this.coarctatio$phase = null;
        if (isCancelable()) {
            setCanceled(false);
        }
        if (hasResult()) {
            setResult(Result.DEFAULT);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void coarctatio$refreshObject(Object object) {
        this.obj = (T) object;
        this.caps.clear();
    }
}
