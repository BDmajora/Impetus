package com.bdmajora.extras.mixin.async;

import com.bdmajora.extras.async.ParallelProcessor;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityTracker;
import net.minecraft.network.Packet;
import org.spongepowered.asm.mixin.Mixin;

// Tracker tables are touched by spawns (track) and deaths (untrack) from any thread and by status packets (sendToTracking) mid-tick; the tracker's own tick runs after the batch, so one monitor over the mutators and senders is enough
@Mixin(EntityTracker.class)
public abstract class EntityTrackerParallelMixin {
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
