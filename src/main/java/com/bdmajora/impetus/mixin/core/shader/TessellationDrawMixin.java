package com.bdmajora.impetus.mixin.core.shader;

import com.bdmajora.impetus.umbra.gl.program.ImmediateTessellation;
import net.minecraft.client.renderer.GlStateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Every immediate-mode draw (vanilla's uploader and the streamed one alike) funnels through glDrawArrays, so this is where a tessellated pack program's quads and triangles become patches
@Mixin(GlStateManager.class)
public abstract class TessellationDrawMixin {
    @Inject(method = "glDrawArrays", at = @At("HEAD"), cancellable = true)
    private static void impetus$drawAsPatches(int mode, int first, int count, CallbackInfo ci) {
        if (ImmediateTessellation.drawArrays(mode, first, count)) {
            ci.cancel();
        }
    }
}
