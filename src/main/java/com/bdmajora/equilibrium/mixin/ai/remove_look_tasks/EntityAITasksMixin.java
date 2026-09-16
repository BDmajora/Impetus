package com.bdmajora.equilibrium.mixin.ai.remove_look_tasks;

import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.ai.EntityAILookIdle;
import net.minecraft.entity.ai.EntityAITasks;
import net.minecraft.entity.ai.EntityAIWatchClosest;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Drops the two cosmetic look tasks at registration (Universal Tweaks' and AI Improvements' "watching AI removal"): EntityAIWatchClosest runs an entity search around every idle mob each tick to find a face to stare at, and EntityAILookIdle only picks a random heading. Off by default since mobs stop turning their heads toward the player
@Mixin(EntityAITasks.class)
public abstract class EntityAITasksMixin {
    @Inject(method = "addTask", at = @At("HEAD"), cancellable = true)
    private void equilibrium$dropLookTasks(int priority, EntityAIBase task, CallbackInfo ci) {
        if (task instanceof EntityAIWatchClosest || task instanceof EntityAILookIdle) {
            ci.cancel();
        }
    }
}
