package com.bdmajora.equilibrium.mixin.entity.fast_hand_swing;

import net.minecraft.entity.EntityLivingBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Mixin that changes EntityLivingBase.
@Mixin(EntityLivingBase.class)
public abstract class EntityLivingBaseMixin {
    // Reference to the field is swing in progress of the target.
    @Shadow
    public boolean isSwingInProgress;

    // Reference to the field swing progress int of the target.
    @Shadow
    public int swingProgressInt;

    // Reference to the field swing progress of the target.
    @Shadow
    public float swingProgress;

    // Runs at the start of updateArmSwingProgress and can cancel it.
    @Inject(method = "updateArmSwingProgress", at = @At("HEAD"), cancellable = true)
    private void equilibrium$skipIdleSwing(CallbackInfo ci) {
        if (!this.isSwingInProgress && this.swingProgressInt == 0 && this.swingProgress == 0.0F) {
            ci.cancel();
        }
    }
}
