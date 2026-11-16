package com.bdmajora.impetus.mixin.features.textures;

import com.google.common.collect.Iterators;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.Stitcher;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import com.bdmajora.impetus.impl.render.texture.BlockAtlasFiltering;
import com.bdmajora.impetus.engine.impl.util.collections.quadtree.QuadTree;
import com.bdmajora.impetus.engine.impl.util.collections.quadtree.Rect2i;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.bdmajora.impetus.ImpetusVintage;
import com.bdmajora.impetus.impl.extensions.SpriteExtension;
import com.bdmajora.impetus.impl.extensions.TextureMapExtension;

import java.util.Iterator;
import java.util.Map;

// Mixin that changes TextureMap.
@Mixin(TextureMap.class)
public class TextureAtlasMixin implements TextureMapExtension {
    @Shadow
    @Final
    private Map<String, TextureAtlasSprite> mapUploadedSprites;

    // Reference to the field mipmap levels of the target.
    @Shadow
    private int mipmapLevels;

    // Sprites by atlas area.
    private QuadTree<TextureAtlasSprite> impetus$quadTree;

    // Width and height of the stitched atlas.
    private int impetus$width, impetus$height;

    // Runs before every return of loadTextureAtlas.
    @Inject(method = "loadTextureAtlas", at = @At("RETURN"))
    private void generateQuadTree(CallbackInfo ci, @Local(ordinal = 0) Stitcher stitcher) {
        this.impetus$width = stitcher.getCurrentWidth();
        this.impetus$height = stitcher.getCurrentHeight();
        Rect2i treeRect = new Rect2i(0, 0, impetus$width, impetus$height);
        int minSize = this.mapUploadedSprites.values().stream().mapToInt(sprite -> Math.max(sprite.getIconWidth(), sprite.getIconHeight())).min().orElse(16);
        this.impetus$quadTree = new QuadTree<>(treeRect, minSize, this.mapUploadedSprites.values(), sprite -> new Rect2i(sprite.getOriginX(), sprite.getOriginY(), sprite.getIconWidth(), sprite.getIconHeight()));

        BlockAtlasFiltering.apply(((AbstractTexture) (Object) this).getGlTextureId());

        com.bdmajora.impetus.umbra.pbr.PBRAtlasManager.rebuild(
                this.mapUploadedSprites, this.impetus$width, this.impetus$height, this.mipmapLevels);
    }

    // Returns the quad tree.
    @Override
    public QuadTree<TextureAtlasSprite> impetus$getQuadTree() {
        return impetus$quadTree;
    }

    // Returns the sprite at a texture coordinate.
    @Override
    public TextureAtlasSprite impetus$findFromUV(float u, float v) {
        if (this.impetus$quadTree == null) {
            return null;
        }

        int x = Math.round(u * this.impetus$width), y = Math.round(v * this.impetus$height);

        return this.impetus$quadTree.find(x, y);
    }

    // Changes an expression value in updateAnimations.
    @ModifyExpressionValue(method = "updateAnimations", at = @At(value = "INVOKE", target = "Ljava/util/List;iterator()Ljava/util/Iterator;", remap = false))
    private Iterator<TextureAtlasSprite> getFilteredIterator(Iterator<TextureAtlasSprite> iterator) {
        if (ImpetusVintage.options().performance.animateOnlyVisibleTextures) {
            return Iterators.filter(iterator, sprite -> ((SpriteExtension)sprite).impetus$shouldUpdate());
        } else {
            return iterator;
        }
    }
}
