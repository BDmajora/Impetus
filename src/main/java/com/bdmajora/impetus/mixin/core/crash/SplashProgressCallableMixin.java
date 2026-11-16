package com.bdmajora.impetus.mixin.core.crash;

import com.bdmajora.impetus.impl.platform.GameWindow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Mixin that changes 1.
@Mixin(targets = {"net/minecraftforge/fml/client/SplashProgress$1"})
public class SplashProgressCallableMixin {
    // Runs at the start of call and can cancel it.
    @Inject(method = "call()Ljava/lang/String;", at = @At("HEAD"), cancellable = true, remap = false)
    private void checkContext(CallbackInfoReturnable<String> cir) {
        boolean isContextAvailable;
        try {
            isContextAvailable = GameWindow.isContextCurrent();
        } catch (Throwable t) {
            isContextAvailable = false;
        }
        if (!isContextAvailable) {
            cir.setReturnValue("No context available");
        }
    }
}
