package com.bdmajora.coarctatio.mixin.client.texture;

import com.bdmajora.coarctatio.MemoryReport;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.resources.IResourceManager;
import net.minecraftforge.fml.common.Loader;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

// Mixin that changes TextureMap.
@Mixin(TextureMap.class)
public abstract class TextureMapMixin {
    @Shadow
    @Final
    private Map<String, TextureAtlasSprite> mapUploadedSprites;

    // Runs before every return of loadTextureAtlas.
    @Inject(method = "loadTextureAtlas", at = @At("RETURN"))
    private void coarctatio$releaseStaticSpriteData(IResourceManager resourceManager, CallbackInfo ci) {
        // The Betweenlands bakes bucket models after stitching and reads the atlas pixels.
        if (Loader.isModLoaded("distanthorizons") || Loader.isModLoaded("thebetweenlands")) {
            return;
        }

        int released = 0;
        long bytes = 0;

        for (TextureAtlasSprite sprite : this.mapUploadedSprites.values()) {
            if (sprite == null || sprite.hasAnimationMetadata()) {
                continue;
            }

            for (int frame = 0; frame < sprite.getFrameCount(); frame++) {
                int[][] mipmaps = sprite.getFrameTextureData(frame);

                if (mipmaps == null) {
                    continue;
                }

                for (int[] level : mipmaps) {
                    if (level != null) {
                        bytes += 16L + level.length * 4L;
                    }
                }
            }

            sprite.clearFramesTextureData();
            released++;
        }

        MemoryReport.recordSpriteBytes(bytes);
    }
}
