package com.bdmajora.impetus.mixin.core.shader;

import com.bdmajora.impetus.umbra.uniforms.CapturedRenderingState;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.IResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Mixin that changes TextureManager.
@Mixin(TextureManager.class)
public class TextureManagerReloadMixin {
    // Runs at the start of onResourceManagerReload.
    @Inject(method = "onResourceManagerReload", at = @At("HEAD"))
    private void impetus$incrementTextureReloadCount(IResourceManager resourceManager, CallbackInfo ci) {
        CapturedRenderingState.INSTANCE.incrementTextureReloadCount();
    }
}
