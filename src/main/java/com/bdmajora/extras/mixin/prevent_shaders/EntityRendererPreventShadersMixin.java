package com.bdmajora.extras.mixin.prevent_shaders;

import com.bdmajora.extras.Extras;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Mixin that changes EntityRenderer.
@Mixin(EntityRenderer.class)
public class EntityRendererPreventShadersMixin {
    // Runs at the start of loadShader and can cancel it.
    @Inject(method = "loadShader", at = @At("HEAD"), cancellable = true)
    private void impetus$preventLoadShader(ResourceLocation shader, CallbackInfo ci) {
        if (Extras.options().render.preventShaders) {
            ci.cancel();
        }
    }

    // Runs at the start of switchUseShader and can cancel it.
    @Inject(method = "switchUseShader", at = @At("HEAD"), cancellable = true)
    private void impetus$preventSwitchUseShader(CallbackInfo ci) {
        if (Extras.options().render.preventShaders) {
            ci.cancel();
        }
    }
}
