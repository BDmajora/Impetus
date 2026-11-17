package com.bdmajora.impetus.mixin.features.textures;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.bdmajora.impetus.impl.extensions.SpriteExtension;

// Mixin that changes TextureAtlasSprite.
@Mixin(TextureAtlasSprite.class)
public abstract class TextureAtlasSpriteMixin implements SpriteExtension {
    // Whether the sprite was drawn since its last animation update.
    private boolean impetus$isActive = false;

    // Marks the sprite as drawn.
    @Override
    public void impetus$markActive() {
        this.impetus$isActive = true;
    }

    // Returns whether the sprite was drawn since the last tick and clears the mark.
    @Override
    public boolean impetus$shouldUpdate() {
        if (this.impetus$isActive) {
            this.impetus$isActive = false;
            return true;
        } else {
            return false;
        }
    }

    // Changes the return value of getMinU and getInterpolatedU.
    @ModifyReturnValue(method = { "getMinU", "getInterpolatedU" }, at = @At("RETURN"))
    private float markActiveWhenGettingCoords(float original) {
        this.impetus$isActive = true;
        return original;
    }
}
