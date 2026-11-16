package com.bdmajora.impetus.mixin.core.shader;

import com.bdmajora.impetus.umbra.gl.program.ImmediateTessellation;
import net.minecraft.client.renderer.GlStateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Mixin that changes GlStateManager.
@Mixin(GlStateManager.class)
public abstract class TessellationDrawMixin {
    // Runs at the start of glDrawArrays and can cancel it.
    @Inject(method = "glDrawArrays", at = @At("HEAD"), cancellable = true)
    private static void impetus$drawAsPatches(int mode, int first, int count, CallbackInfo ci) {
        if (ImmediateTessellation.drawArrays(mode, first, count)) {
            ci.cancel();
        }
    }
}
