package com.bdmajora.equilibrium.mixin.ai.remove_look_tasks;

import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.ai.EntityAILookIdle;
import net.minecraft.entity.ai.EntityAITasks;
import net.minecraft.entity.ai.EntityAIWatchClosest;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Mixin that changes EntityAITasks.
@Mixin(EntityAITasks.class)
public abstract class EntityAITasksMixin {
    // Runs at the start of addTask and can cancel it.
    @Inject(method = "addTask", at = @At("HEAD"), cancellable = true)
    private void equilibrium$dropLookTasks(int priority, EntityAIBase task, CallbackInfo ci) {
        if (task instanceof EntityAIWatchClosest || task instanceof EntityAILookIdle) {
            ci.cancel();
        }
    }
}
