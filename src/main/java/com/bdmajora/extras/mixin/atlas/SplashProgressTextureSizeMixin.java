package com.bdmajora.extras.mixin.atlas;

import com.bdmajora.extras.Extras;
import net.minecraftforge.fml.client.SplashProgress;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Forge's copy of the same probe, used while the splash screen owns the context. remap = false, Forge class
@Mixin(value = SplashProgress.class, remap = false)
public abstract class SplashProgressTextureSizeMixin {
    private static int impetus$maxTextureSize = -1;

    @Inject(method = "getMaxTextureSize", at = @At("HEAD"), cancellable = true)
    private static void impetus$queryDriverLimit(CallbackInfoReturnable<Integer> cir) {
        if (!Extras.options().loading.driverAtlasLimit) {
            return;
        }
        if (impetus$maxTextureSize <= 0) {
            impetus$maxTextureSize = GL11.glGetInteger(GL11.GL_MAX_TEXTURE_SIZE);
            if (impetus$maxTextureSize <= 0) {
                return;
            }
        }
        cir.setReturnValue(impetus$maxTextureSize);
    }
}
