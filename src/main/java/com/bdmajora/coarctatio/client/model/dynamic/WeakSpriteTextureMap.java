package com.bdmajora.coarctatio.client.model.dynamic;

import net.minecraft.util.ResourceLocation;

// Texture map that can register sprites it may release.
public interface WeakSpriteTextureMap {
    // Registers the sprite location without keeping the sprite.
    void coarctatio$registerSpriteWeak(ResourceLocation location);
}
