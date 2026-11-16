package com.bdmajora.impetus.mixin.features.mipmaps;

import net.minecraft.client.renderer.texture.TextureUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import static com.bdmajora.impetus.engine.impl.texture.MipmapHelper.weightedAverageColor;

// Mixin that changes TextureUtil.
@Mixin(value = TextureUtil.class, priority = 900)
public class TextureUtilMixin {
    // Replaces the whole method blendColors.
    @Overwrite
    private static int blendColors(int one, int two, int three, int four, boolean checkAlpha) {
        return weightedAverageColor(weightedAverageColor(one, two), weightedAverageColor(three, four));
    }
}
