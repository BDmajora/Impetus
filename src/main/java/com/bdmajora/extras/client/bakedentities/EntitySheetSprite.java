package com.bdmajora.extras.client.bakedentities;

import com.bdmajora.extras.Extras;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.function.Function;

// An entity texture sheet on the block atlas. Vanilla's loader throws "broken aspect ratio" for any sprite that is not square and not animated, and sign.png is 64x32, so the sheet would be quietly swapped for the missing sprite (magenta signs); this reads its own size and frame instead and leaves only the mipmaps to the atlas
final class EntitySheetSprite extends TextureAtlasSprite {
    // The atlas's level count, fixed for the stitch this sprite was registered for
    private final int mipmapLevels;

    EntitySheetSprite(ResourceLocation location, int mipmapLevels) {
        super(location.toString());
        this.mipmapLevels = mipmapLevels;
    }

    @Override
    public boolean hasCustomLoader(IResourceManager manager, ResourceLocation location) {
        return true;
    }

    // False means stitch it; the atlas then runs generateMipmaps over the frame stored here, which reads one slot per level, so the array is sized for the atlas's level count
    @Override
    public boolean load(IResourceManager manager, ResourceLocation location, Function<ResourceLocation, TextureAtlasSprite> textureGetter) {
        try (IResource resource = manager.getResource(location)) {
            BufferedImage image = TextureUtil.readBufferedImage(resource.getInputStream());
            int width = image.getWidth();
            int height = image.getHeight();
            int[][] frame = new int[this.mipmapLevels + 1][];
            frame[0] = new int[width * height];
            image.getRGB(0, 0, width, height, frame[0], 0, width);
            this.setIconWidth(width);
            this.setIconHeight(height);
            this.clearFramesTextureData();
            this.framesTextureData.add(frame);
            return false;
        } catch (IOException | RuntimeException e) {
            // Left unstitched, which lands it on the missing sprite the way vanilla would; logged here since the FML texture report is silenced
            Extras.LOGGER.warn("Could not load entity sheet {}: {}", location, e.toString());
            return true;
        }
    }
}
