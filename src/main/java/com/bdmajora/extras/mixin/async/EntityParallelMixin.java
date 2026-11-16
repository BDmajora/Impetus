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

// Mixin that changes Entity.
@Mixin(Entity.class)
public abstract class EntityParallelMixin implements PortalAccessor {
    // Field next id added to the target.
    @Unique
    private static final AtomicInteger impetus$nextId = new AtomicInteger();

    // Reference to the field in portal of the target.
    @Shadow
    protected boolean inPortal;

    // Reference to the field entity id of the target.
    @Shadow
    private int entityId;

    // Runs before every return of constructor.
    @Inject(method = "<init>", at = @At("RETURN"))
    private void impetus$atomicId(CallbackInfo ci) {
        this.entityId = impetus$nextId.getAndIncrement();
    }

    // Runs before every return of resetEntityId.
    @Inject(method = "resetEntityId", at = @At("RETURN"), remap = false)
    private void impetus$atomicResetId(CallbackInfo ci) {
        this.entityId = impetus$nextId.getAndIncrement();
    }

    // Returns whether in portal.
    @Override
    public boolean impetus$isInPortal() {
        return this.inPortal;
    }

    // Handles locked add passenger.
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

    // Handles locked remove passenger.
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
