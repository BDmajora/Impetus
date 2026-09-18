package com.bdmajora.extras.mixin.bakedentities;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

// Exposes the pre-stitch registry so an entity sheet can replace a plain registration of the same name; setTextureEntry refuses a name already present
@Mixin(TextureMap.class)
public interface TextureMapRegisteredSpritesAccessor {
    @Accessor("mapRegisteredSprites")
    Map<String, TextureAtlasSprite> impetus$getRegisteredSprites();
}
