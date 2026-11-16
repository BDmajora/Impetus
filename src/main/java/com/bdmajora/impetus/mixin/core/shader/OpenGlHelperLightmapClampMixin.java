package com.bdmajora.impetus.mixin.core.shader;

import com.bdmajora.impetus.umbra.Umbra;
import net.minecraft.client.renderer.OpenGlHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

// Mixin that changes OpenGlHelper.
@Mixin(OpenGlHelper.class)
public class OpenGlHelperLightmapClampMixin {
    // Largest lightmap coordinate.
    private static final float MAX_LIGHTMAP_COORD = 240.0f;

    // Changes a local variable in setLightmapTextureCoords.
    @ModifyVariable(method = "setLightmapTextureCoords(IFF)V", at = @At("HEAD"), argsOnly = true, ordinal = 0,
            require = 0)
    private static float impetus$clampBlockLight(float blockLight) {
        return impetus$clamp(blockLight);
    }

    // Changes a local variable in setLightmapTextureCoords.
    @ModifyVariable(method = "setLightmapTextureCoords(IFF)V", at = @At("HEAD"), argsOnly = true, ordinal = 1,
            require = 0)
    private static float impetus$clampSkyLight(float skyLight) {
        return impetus$clamp(skyLight);
    }

    // Handles clamp.
    private static float impetus$clamp(float coord) {
        if (coord <= MAX_LIGHTMAP_COORD || Umbra.getRenderingPipeline() == null) {
            return coord;
        }
        return MAX_LIGHTMAP_COORD;
    }
}
