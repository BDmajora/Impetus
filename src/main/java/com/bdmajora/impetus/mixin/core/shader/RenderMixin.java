package com.bdmajora.impetus.mixin.core.shader;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.renderer.entity.Render;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.bdmajora.impetus.umbra.Umbra;
import com.bdmajora.impetus.umbra.pipeline.UmbraRenderingPipeline;

// Mixin that changes Render.
@Mixin(Render.class)
public class RenderMixin {
    // Changes an expression value in doRenderShadowAndFire.
    @ModifyExpressionValue(method = "doRenderShadowAndFire",
            at = @At(value = "FIELD", target = "Lnet/minecraft/client/settings/GameSettings;entityShadows:Z"))
    private boolean impetus$disableVanillaEntityShadowsWithShaderShadows(boolean entityShadows) {
        UmbraRenderingPipeline pipeline = Umbra.getRenderingPipeline();
        return entityShadows && (pipeline == null || !pipeline.shouldDisableVanillaEntityShadows());
    }
}
