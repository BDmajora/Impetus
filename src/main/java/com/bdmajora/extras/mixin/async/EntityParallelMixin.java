package com.bdmajora.extras.mixin.async;

import com.bdmajora.extras.async.ParallelProcessor;
import com.bdmajora.extras.async.PortalAccessor;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.atomic.AtomicInteger;

// Entity id allocation made atomic (two workers constructing drops at once must not share an id, since the tracker keys on it) and the passenger list guarded on the vehicle; rider-side fields are left alone so no rider->vehicle->rider lock cycle can form
@Mixin(Entity.class)
public abstract class EntityParallelMixin implements PortalAccessor {
    @Unique
    private static final AtomicInteger impetus$nextId = new AtomicInteger();

    @Shadow
    protected boolean inPortal;

    @Shadow
    private int entityId;

    // Vanilla's nextEntityID++ still runs but its result is overwritten; the static keeps drifting upward so nothing else that reads it is surprised
    @Inject(method = "<init>", at = @At("RETURN"))
    private void impetus$atomicId(CallbackInfo ci) {
        this.entityId = impetus$nextId.getAndIncrement();
    }

    // Forge's deprecated reset takes the same path; remap is off because the method is a Forge patch with no obfuscated name
    @Inject(method = "resetEntityId", at = @At("RETURN"), remap = false)
    private void impetus$atomicResetId(CallbackInfo ci) {
        this.entityId = impetus$nextId.getAndIncrement();
    }

    @Override
    public boolean impetus$isInPortal() {
        return this.inPortal;
    }

    @WrapMethod(method = "addPassenger")
    private void impetus$lockedAddPassenger(Entity passenger, Operation<Void> original) {
        if (!ParallelProcessor.INSTALLED) {
            original.call(passenger);
            return;
        }
        synchronized (this) {
            original.call(passenger);
        }
    }

    @WrapMethod(method = "removePassenger")
    private void impetus$lockedRemovePassenger(Entity passenger, Operation<Void> original) {
        if (!ParallelProcessor.INSTALLED) {
            original.call(passenger);
            return;
        }
        synchronized (this) {
            original.call(passenger);
        }
    }
}
