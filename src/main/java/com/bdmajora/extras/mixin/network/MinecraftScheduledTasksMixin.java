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

// Mixin that changes Minecraft.
@Mixin(Minecraft.class)
public abstract class MinecraftScheduledTasksMixin {
    // Reference to the field scheduled tasks of the target.
    @Shadow
    @Final
    private Queue<FutureTask<?>> scheduledTasks;

    // Reference to the field logger of the target.
    @Shadow
    @Final
    private static Logger LOGGER;

    // Runs inside runGameLoop.
    @Inject(method = "runGameLoop()V", at = @At(value = "FIELD", target = "Lnet/minecraft/client/Minecraft;scheduledTasks:Ljava/util/Queue;", opcode = Opcodes.GETFIELD, ordinal = 0))
    private void impetus$drainOutsideLock(CallbackInfo ci) {
        ScheduledTaskDrain.drain(this.scheduledTasks, LOGGER);
    }

    // Changes an expression value in runGameLoop.
    @ModifyExpressionValue(method = "runGameLoop()V", at = @At(value = "INVOKE", target = "Ljava/util/Queue;isEmpty()Z", remap = false))
    private boolean impetus$skipLockedDrain(boolean empty) {
        return true;
    }
}
