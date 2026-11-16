package com.bdmajora.impetus.mixin.core.shader;

import com.bdmajora.impetus.umbra.Umbra;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.client.renderer.block.model.IBakedModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Mixin that changes RenderItem.
@Mixin(RenderItem.class)
public class RenderItemGlintMixin {
    // Runs at the start of renderEffect.
    @Inject(method = "renderEffect", at = @At("HEAD"), require = 0)
    private void impetus$beginItemGlint(IBakedModel model, CallbackInfo ci) {
        Umbra.beginArmorGlint();
    }

    // Runs before every return of renderEffect.
    @Inject(method = "renderEffect", at = @At("RETURN"), require = 0)
    private void impetus$endItemGlint(IBakedModel model, CallbackInfo ci) {
        Umbra.endArmorGlint();
    }
}
