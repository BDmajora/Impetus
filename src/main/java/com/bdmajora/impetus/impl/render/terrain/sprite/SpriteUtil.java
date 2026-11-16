package com.bdmajora.impetus.impl.render.terrain.sprite;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import com.bdmajora.impetus.impl.extensions.SpriteExtension;

// Helpers for sprite animation.
public class SpriteUtil {
    // Marks the sprite active.
    public static void markSpriteActive(TextureAtlasSprite sprite) {
        ((SpriteExtension)sprite).impetus$markActive();
    }
}
