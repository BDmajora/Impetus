package net.minecraft.client.renderer.texture;

import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;

// Sprite with its own loader.
public class CustomLoaderSprite extends TextureAtlasSprite {
    public CustomLoaderSprite(String name) {
        super(name);
    }

    @Override
    public boolean hasCustomLoader(IResourceManager manager, ResourceLocation location) {
        return true;
    }
}
