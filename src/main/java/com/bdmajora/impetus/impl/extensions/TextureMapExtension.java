package com.bdmajora.impetus.impl.extensions;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import com.bdmajora.impetus.engine.impl.util.collections.quadtree.QuadTree;

// Contract of the texture map extension.
public interface TextureMapExtension {
    // Returns the quad tree.
    QuadTree<TextureAtlasSprite> impetus$getQuadTree();

    // Returns the sprite at a texture coordinate.
    TextureAtlasSprite impetus$findFromUV(float u, float v);
}
