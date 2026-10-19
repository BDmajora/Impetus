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

@Mixin(TextureMap.class)
public class TextureAtlasMixin implements TextureMapExtension {
    @Shadow
    @Final
    private Map<String, TextureAtlasSprite> mapUploadedSprites;

    @Shadow
    private int mipmapLevels;

    private QuadTree<TextureAtlasSprite> impetus$quadTree;

    private int impetus$width, impetus$height;

    @Inject(method = "loadTextureAtlas", at = @At("RETURN"))
    private void generateQuadTree(CallbackInfo ci, @Local(ordinal = 0) Stitcher stitcher) {
        this.impetus$width = stitcher.getCurrentWidth();
        this.impetus$height = stitcher.getCurrentHeight();
        Rect2i treeRect = new Rect2i(0, 0, impetus$width, impetus$height);
        int minSize = this.mapUploadedSprites.values().stream().mapToInt(sprite -> Math.max(sprite.getIconWidth(), sprite.getIconHeight())).min().getAsInt();
        this.impetus$quadTree = new QuadTree<>(treeRect, minSize, this.mapUploadedSprites.values(), sprite -> new Rect2i(sprite.getOriginX(), sprite.getOriginY(), sprite.getIconWidth(), sprite.getIconHeight()));

        // Apply the configured texture/pixel filtering and anisotropy to the freshly (re)built block atlas.
        BlockAtlasFiltering.apply(((AbstractTexture) (Object) this).getGlTextureId());

        // Build the shader-pipeline PBR atlases (normals/specular companions) mirroring this atlas's layout.
        com.bdmajora.impetus.umbra.pbr.PBRAtlasManager.rebuild(
                this.mapUploadedSprites, this.impetus$width, this.impetus$height, this.mipmapLevels);
    }

    @Override
    public QuadTree<TextureAtlasSprite> impetus$getQuadTree() {
        return impetus$quadTree;
    }

    @Override
    public TextureAtlasSprite impetus$findFromUV(float u, float v) {
        int x = Math.round(u * this.impetus$width), y = Math.round(v * this.impetus$height);

        return this.impetus$quadTree.find(x, y);
    }

    // Skips sprites nothing drew last frame when the animate-only-visible option is on
    @ModifyExpressionValue(method = "updateAnimations", at = @At(value = "INVOKE", target = "Ljava/util/List;iterator()Ljava/util/Iterator;", remap = false))
    private Iterator<TextureAtlasSprite> getFilteredIterator(Iterator<TextureAtlasSprite> iterator) {
        if (ImpetusVintage.options().performance.animateOnlyVisibleTextures) {
            return Iterators.filter(iterator, sprite -> ((SpriteExtension)sprite).impetus$shouldUpdate());
        } else {
            return iterator;
        }
    }
}
