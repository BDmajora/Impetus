package com.bdmajora.coarctatio.client.model.dynamic;

import net.minecraft.util.ResourceLocation;

// Implemented on TextureMap by mixin; a weak registration is one the texture scan made, which a mod's own registerSprite or setTextureEntry for the same name may still replace
public interface WeakSpriteTextureMap {
    void coarctatio$registerSpriteWeak(ResourceLocation location);
}
