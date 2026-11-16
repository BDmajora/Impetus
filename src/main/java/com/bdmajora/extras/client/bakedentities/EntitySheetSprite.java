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

// Sprite that loads a whole entity texture sheet.
final class EntitySheetSprite extends TextureAtlasSprite {
    // Number of mipmap levels.
    private final int mipmapLevels;

    // Creates a sprite for the location.
    EntitySheetSprite(ResourceLocation location, int mipmapLevels) {
        super(location.toString());
        this.mipmapLevels = mipmapLevels;
    }

    // Returns true.
    @Override
    public boolean hasCustomLoader(IResourceManager manager, ResourceLocation location) {
        return true;
    }

    // Reads the sheet and stores its frames.
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
            Extras.LOGGER.warn("Could not load entity sheet {}: {}", location, e.toString());
            return true;
        }
    }
}
