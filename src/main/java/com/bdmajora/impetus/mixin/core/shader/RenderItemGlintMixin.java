package com.bdmajora.impetus.mixin.core.shader;

import com.bdmajora.impetus.umbra.Umbra;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.client.renderer.block.model.IBakedModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// The held/dropped-item half of the enchantment glint (counterpart to LayerArmorBaseGlintMixin), bracketing renderEffect like OptiFine; no renderItemGui gate needed since beginArmorGlint() already no-ops outside world rendering
@Mixin(RenderItem.class)
public class RenderItemGlintMixin {
    @Inject(method = "renderEffect", at = @At("HEAD"), require = 0)
    private void impetus$beginItemGlint(IBakedModel model, CallbackInfo ci) {
        Umbra.beginArmorGlint();
    }

    @Inject(method = "renderEffect", at = @At("RETURN"), require = 0)
    private void impetus$endItemGlint(IBakedModel model, CallbackInfo ci) {
        Umbra.endArmorGlint();
    }
}
