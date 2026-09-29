package net.minecraft.client.renderer.texture;

import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;

// A sprite that loads itself, which the prefetch has to leave alone; the constructor and the hook are both
// protected, so this stand-in lives in the sprite's own package
public class CustomLoaderSprite extends TextureAtlasSprite {
    public CustomLoaderSprite(String name) {
        super(name);
    }

    @Override
    public boolean hasCustomLoader(IResourceManager manager, ResourceLocation location) {
        return true;
    }
}
