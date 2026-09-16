package com.bdmajora.coarctatio.mixin.client.texture;

import com.bdmajora.coarctatio.client.texture.SpritePrefetch;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

// Takes the image the prefetch already decoded instead of running the PNG decoder again on the client thread
@Mixin(TextureAtlasSprite.class)
public abstract class TextureAtlasSpritePrefetchMixin {
    @WrapOperation(method = "loadSpriteFrames", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/texture/TextureUtil;readBufferedImage(Ljava/io/InputStream;)Ljava/awt/image/BufferedImage;"))
    private BufferedImage coarctatio$takePrefetchedImage(InputStream stream, Operation<BufferedImage> original) throws IOException {
        if (stream instanceof SpritePrefetch.PrefetchedStream) {
            BufferedImage image = ((SpritePrefetch.PrefetchedStream) stream).resource().takeImage();
            if (image != null) {
                return image;
            }
        }
        return original.call(stream);
    }
}
