package com.bdmajora.impetus.mixin.features.mipmaps;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import com.bdmajora.impetus.engine.api.util.ColorARGB;
import com.bdmajora.impetus.engine.impl.render.chunk.sprite.SpriteTransparencyLevel;
import com.bdmajora.impetus.engine.impl.util.color.ColorSRGB;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.bdmajora.impetus.impl.extensions.SpriteExtension;

import java.util.List;

// Mixin that changes TextureAtlasSprite.
@Mixin(TextureAtlasSprite.class)
public abstract class TextureAtlasSpriteMixin implements SpriteExtension, SpriteTransparencyLevel.Holder {
    // Reference to the field frames texture data of the target.
    @Shadow
    protected List<int[][]> framesTextureData;

    // Reference to the field icon name of the target.
    @Shadow
    @Final
    private String iconName;

    // Transparency level of the sprite.
    private SpriteTransparencyLevel impetus$transparencyLevel = SpriteTransparencyLevel.TRANSLUCENT;

    // Runs at the start of generateMipmaps.
    @Inject(method = "generateMipmaps", at = @At("HEAD"))
    private void processSprite(int mipLevel, CallbackInfo ci) {
        if (this.framesTextureData == null) {
            return;
        }
        SpriteTransparencyLevel level = SpriteTransparencyLevel.OPAQUE;
        boolean shouldRewriteColors = mipLevel > 0 && iconName != null && isBlockTexture(iconName) && !iconName.contains("leaves");
        for (int[][] frame : this.framesTextureData) {
            if (frame != null && frame[0] != null) {
                level = impetus$processTransparentImages(level, frame[0], shouldRewriteColors);
            }
        }
        this.impetus$transparencyLevel = level;
    }

    // Returns whether a sprite name is a block texture.
    private static boolean isBlockTexture(String iconName) {
        return iconName.startsWith("block", iconName.indexOf(':') + 1);
    }

    // Handles process transparent images.
    private SpriteTransparencyLevel impetus$processTransparentImages(SpriteTransparencyLevel prevLevel, int[] nativeImage, boolean shouldRewriteColors) {
        float r = 0.0f;
        float g = 0.0f;
        float b = 0.0f;

        float totalWeight = 0.0f;

        SpriteTransparencyLevel level = prevLevel;

        for (int y = 0; y < nativeImage.length; y++) {
            int color = nativeImage[y];
            int alpha = ColorARGB.unpackAlpha(color);

            if (alpha > 0) {
                if(alpha < 255) {
                    level = level.chooseNextLevel(SpriteTransparencyLevel.TRANSLUCENT);
                } else {
                    level = level.chooseNextLevel(SpriteTransparencyLevel.OPAQUE);
                }

                if (shouldRewriteColors) {
                    float weight = (float) alpha;

                    r += ColorSRGB.srgbToLinear(ColorARGB.unpackRed(color)) * weight;
                    g += ColorSRGB.srgbToLinear(ColorARGB.unpackGreen(color)) * weight;
                    b += ColorSRGB.srgbToLinear(ColorARGB.unpackBlue(color)) * weight;

                    totalWeight += weight;
                }
            } else {
                level = level.chooseNextLevel(SpriteTransparencyLevel.TRANSPARENT);
            }
        }

        if (!shouldRewriteColors || totalWeight == 0.0f) {
            return level;
        }

        r /= totalWeight;
        g /= totalWeight;
        b /= totalWeight;

        int averageColor = ColorSRGB.linearToSrgb(r, g, b, 0);

        for (int y = 0; y < nativeImage.length; y++) {
            int color = nativeImage[y];
            int alpha = ColorARGB.unpackAlpha(color);

            if (alpha == 0) {
                nativeImage[y] = averageColor;
            }
        }

        return level;
    }

    // Returns the transparency level.
    @Override
    public SpriteTransparencyLevel impetus$getTransparencyLevel() {
        return this.impetus$transparencyLevel;
    }
}
