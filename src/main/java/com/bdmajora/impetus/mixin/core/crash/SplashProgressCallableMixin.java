package com.bdmajora.impetus.mixin.core.crash;

import com.bdmajora.impetus.impl.platform.GameWindow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Patches SplashProgress' "GL info" crash callable; Cleanroom only checks for the client thread, but LWJGL3 throws from glGetString on any thread without a current context, which crashes the crash report itself
@Mixin(targets = {"net/minecraftforge/fml/client/SplashProgress$1"})
public class SplashProgressCallableMixin {
    // Reports GL info only when the window's context is current here
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
