package com.bdmajora.extras.mixin.async;

import com.bdmajora.extras.async.ParallelProcessor;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityTracker;
import net.minecraft.network.Packet;
import org.spongepowered.asm.mixin.Mixin;

// Mixin that changes EntityTracker.
@Mixin(EntityTracker.class)
public abstract class EntityTrackerParallelMixin {
    // Handles locked track.
    @WrapMethod(method = "track(Lnet/minecraft/entity/Entity;IIZ)V")
    private void impetus$lockedTrack(Entity entity, int range, int frequency, boolean sendVelocity, Operation<Void> original) {
        if (!ParallelProcessor.INSTALLED) {
            original.call(entity, range, frequency, sendVelocity);
            return;
        }
        synchronized (this) {
            original.call(entity, range, frequency, sendVelocity);
        }
    }

    // Handles locked untrack.
    @WrapMethod(method = "untrack")
    private void impetus$lockedUntrack(Entity entity, Operation<Void> original) {
        if (!ParallelProcessor.INSTALLED) {
            original.call(entity);
            return;
        }
        synchronized (this) {
            original.call(entity);
        }
    }

    // Handles locked send to tracking.
    @WrapMethod(method = "sendToTracking")
    private void impetus$lockedSendToTracking(Entity entity, Packet<?> packet, Operation<Void> original) {
        if (!ParallelProcessor.INSTALLED) {
            original.call(entity, packet);
            return;
        }
        synchronized (this) {
            original.call(entity, packet);
        }
    }

    // Handles locked send to tracking and self.
    @WrapMethod(method = "sendToTrackingAndSelf")
    private void impetus$lockedSendToTrackingAndSelf(Entity entity, Packet<?> packet, Operation<Void> original) {
        if (!ParallelProcessor.INSTALLED) {
            original.call(entity, packet);
            return;
        }
        synchronized (this) {
            original.call(entity, packet);
        }
    }

    // Handles locked tick.
    @WrapMethod(method = "tick")
    private void impetus$lockedTick(Operation<Void> original) {
        if (!ParallelProcessor.INSTALLED) {
            original.call();
            return;
        }
        synchronized (this) {
            original.call();
        }
    }
}
