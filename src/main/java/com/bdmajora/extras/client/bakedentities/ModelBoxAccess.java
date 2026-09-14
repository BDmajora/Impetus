package com.bdmajora.extras.client.bakedentities;

import net.minecraft.client.model.TexturedQuad;

// Implemented on ModelBox by the accessor mixin; the quad list is private and is the only geometry the box holds
public interface ModelBoxAccess {
    TexturedQuad[] impetus$getQuads();
}
