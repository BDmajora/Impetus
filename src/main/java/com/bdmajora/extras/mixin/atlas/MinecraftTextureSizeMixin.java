package com.bdmajora.extras.mixin.atlas;

import com.bdmajora.extras.Extras;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Vanilla probes the largest atlas the driver accepts by issuing proxy texture uploads from 16384 down, which stalls startup and, on some drivers, answers smaller than the real limit; GL_MAX_TEXTURE_SIZE is the driver's own word (Universal Tweaks' and Valkyrie's atlas size). Read once and kept
@Mixin(Minecraft.class)
public abstract class MinecraftTextureSizeMixin {
    private static int impetus$maxTextureSize = -1;

    @Inject(method = "getGLMaximumTextureSize", at = @At("HEAD"), cancellable = true)
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
