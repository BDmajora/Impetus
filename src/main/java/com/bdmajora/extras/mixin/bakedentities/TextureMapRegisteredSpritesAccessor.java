package com.bdmajora.extras.mixin.bakedentities;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

// Mixin that changes TextureMap.
@Mixin(TextureMap.class)
public interface TextureMapRegisteredSpritesAccessor {
    // Gives access to the field get registered sprites.
    @Accessor("mapRegisteredSprites")
    Map<String, TextureAtlasSprite> impetus$getRegisteredSprites();
}
