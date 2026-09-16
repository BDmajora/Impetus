package com.bdmajora.equilibrium.mixin.advancements.inventory_trigger;

import com.bdmajora.equilibrium.common.advancements.StackSizeThresholds;
import net.minecraft.advancements.AdvancementManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// A reload re-deserialises every criterion, so the threshold set starts over with it
@Mixin(AdvancementManager.class)
public abstract class AdvancementManagerMixin {
    @Inject(method = "reload", at = @At("HEAD"))
    private void equilibrium$resetThresholds(CallbackInfo ci) {
        StackSizeThresholds.clear();
    }
}
