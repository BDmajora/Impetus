package com.bdmajora.coarctatio.mixin.client.texture;

import com.bdmajora.coarctatio.Coarctatio;
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

// Releases static sprites' raw pixel data (full mipmap chain) after GPU upload since nothing reads it again; animated sprites are skipped, and it is config-gated because some CTM/custom-atlas mods read pixels post-build
@Mixin(TextureMap.class)
public abstract class TextureMapMixin {
    @Shadow
    @Final
    private Map<String, TextureAtlasSprite> mapUploadedSprites;

    @Inject(method = "loadTextureAtlas", at = @At("RETURN"))
    private void coarctatio$releaseStaticSpriteData(IResourceManager resourceManager, CallbackInfo ci) {
        // Distant Horizons colours and textures its LODs from these pixels, reading them on its own threads whenever a block first appears in a LOD
        if (Loader.isModLoaded("distanthorizons")) {
            return;
        }

        int released = 0;
        long bytes = 0;

        for (TextureAtlasSprite sprite : this.mapUploadedSprites.values()) {
            if (sprite == null || sprite.hasAnimationMetadata()) {
                continue;
            }

            // Measured before the release so the report quotes a real figure; one of only two places Coarctatio knows exactly what it freed
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
