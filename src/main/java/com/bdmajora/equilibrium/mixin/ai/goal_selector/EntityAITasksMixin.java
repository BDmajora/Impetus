package com.bdmajora.equilibrium.mixin.ai.goal_selector;

import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import net.minecraft.entity.ai.EntityAITasks;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

// Mixin that changes EntityAITasks.
@Mixin(EntityAITasks.class)
public class EntityAITasksMixin {
    // Reference to the field task entries of the target.
    @Shadow
    @Final
    @Mutable
    public Set<EntityAITasks.EntityAITaskEntry> taskEntries;

    // Reference to the field executing task entries of the target.
    @Shadow
    @Final
    @Mutable
    private Set<EntityAITasks.EntityAITaskEntry> executingTaskEntries;

    // Runs before every return of constructor.
    @Inject(method = "<init>", at = @At("RETURN"))
    private void equilibrium$useFastutilSets(CallbackInfo ci) {
        this.taskEntries = new ObjectLinkedOpenHashSet<>();
        this.executingTaskEntries = new ObjectLinkedOpenHashSet<>();
    }
}
