package com.bdmajora.extras.mixin.network;

import com.bdmajora.extras.network.ScheduledTaskDrain;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.Minecraft;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Queue;
import java.util.concurrent.FutureTask;

// Moves the scheduled task drain out from under the queue's monitor (ScheduledTaskDrain has the why). The drain runs just before vanilla's synchronized block, inside its "scheduledExecutables" profiler section, and vanilla's loop is then told the queue is empty so a task the network thread slips in between cannot end up running under the lock after all
@Mixin(Minecraft.class)
public abstract class MinecraftScheduledTasksMixin {
    @Shadow
    @Final
    private Queue<FutureTask<?>> scheduledTasks;

    @Shadow
    @Final
    private static Logger LOGGER;

    @Inject(method = "runGameLoop()V", at = @At(value = "FIELD", target = "Lnet/minecraft/client/Minecraft;scheduledTasks:Ljava/util/Queue;", opcode = Opcodes.GETFIELD, ordinal = 0))
    private void impetus$drainOutsideLock(CallbackInfo ci) {
        ScheduledTaskDrain.drain(this.scheduledTasks, LOGGER);
    }

    @ModifyExpressionValue(method = "runGameLoop()V", at = @At(value = "INVOKE", target = "Ljava/util/Queue;isEmpty()Z"))
    private boolean impetus$skipLockedDrain(boolean empty) {
        return true;
    }
}
