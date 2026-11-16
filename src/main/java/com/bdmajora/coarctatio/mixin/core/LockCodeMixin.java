package com.bdmajora.coarctatio.mixin.core;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.LockCode;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Mixin that changes LockCode.
@Mixin(LockCode.class)
public abstract class LockCodeMixin {
    // Reference to the field empty code of the target.
    @Shadow
    @Final
    public static LockCode EMPTY_CODE;

    // Runs at the start of fromNBT and can cancel it.
    @Inject(method = "fromNBT", at = @At("HEAD"), cancellable = true)
    private static void coarctatio$shareEmptyLock(NBTTagCompound nbt, CallbackInfoReturnable<LockCode> cir) {
        if (nbt.hasKey("Lock", 8) && nbt.getString("Lock").isEmpty()) {
            cir.setReturnValue(EMPTY_CODE);
        }
    }
}
