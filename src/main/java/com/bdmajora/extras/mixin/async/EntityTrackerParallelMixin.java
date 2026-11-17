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
    // Starts tracking an entity under the tracker lock.
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

    // Stops tracking an entity under the tracker lock.
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

    // Sends a packet to an entity's watchers under the tracker lock.
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

    // Sends a packet to an entity and its watchers under the tracker lock.
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

    // Updates every tracked entity under the tracker lock.
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
