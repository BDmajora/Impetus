package com.bdmajora.impetus.umbra.pbr;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.client.resources.IResource;
import net.minecraft.util.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Map;

// Owner of the normal and specular atlases.
public final class PBRAtlasManager {
    // Logger of this class.
    private static final Logger LOGGER = LogManager.getLogger("Impetus/Umbra");

    // Gl texture 2 d.
    private static final int GL_TEXTURE_2D = 0x0DE1;
    // GL enum of the magnify filter.
    private static final int GL_TEXTURE_MAG_FILTER = 0x2800;
    // GL enum of the minify filter.
    private static final int GL_TEXTURE_MIN_FILTER = 0x2801;
    // GL enum of the s wrap.
    private static final int GL_TEXTURE_WRAP_S = 0x2802;
    // GL enum of the t wrap.
    private static final int GL_TEXTURE_WRAP_T = 0x2803;
    // GL enum of nearest filtering.
    private static final int GL_NEAREST = 0x2600;
    // GL enum of nearest mipmap filtering.
    private static final int GL_NEAREST_MIPMAP_NEAREST = 0x2700;
    // GL enum of edge clamping.
    private static final int GL_CLAMP_TO_EDGE = 0x812F;

    // Normal atlas name or minus one.
    private static int normalsAtlas = -1;
    // Specular atlas name or minus one.
    private static int specularAtlas = -1;
    // Number of sprites with normals.
    private static int normalsCount;
    // Number of sprites with specular data.
    private static int specularCount;

    // Prevents instantiation
    private PBRAtlasManager() {
    }

    // Returns the normal atlas or a fallback.
    public static int getNormalsAtlas(int fallback) {
        return normalsCount > 0 ? normalsAtlas : fallback;
    }

    // Returns the specular atlas or a fallback.
    public static int getSpecularAtlas(int fallback) {
        return specularCount > 0 ? specularAtlas : fallback;
    }

    // Rebuilds both atlases from the sprites.
    public static void rebuild(Map<String, TextureAtlasSprite> sprites, int atlasWidth, int atlasHeight, int mipmapLevels) {
        try {
            destroy();

            normalsAtlas = allocateAtlas(atlasWidth, atlasHeight, mipmapLevels, 0xFF7F7FFF);
            specularAtlas = allocateAtlas(atlasWidth, atlasHeight, mipmapLevels, 0x00000000);
            normalsCount = 0;
            specularCount = 0;

            for (TextureAtlasSprite sprite : sprites.values()) {
                if (sprite.getIconWidth() <= 0 || sprite.getIconHeight() <= 0) {
                    continue;
                }
                if (uploadCompanion(sprite, "_n", normalsAtlas, mipmapLevels)) {
                    normalsCount++;
                }
                if (uploadCompanion(sprite, "_s", specularAtlas, mipmapLevels)) {
                    specularCount++;
                }
            }

            GlStateManager.bindTexture(normalsAtlas);
            applyPbrSampling(mipmapLevels);
            GlStateManager.bindTexture(specularAtlas);
            applyPbrSampling(mipmapLevels);

            GlStateManager.bindTexture(0);
        } catch (Throwable t) {
            LOGGER.error("[Umbra] Failed to build PBR atlases; packs will see flat surfaces", t);
            destroy();
        }
    }

    // Deletes the atlases.
    public static void destroy() {
        if (normalsAtlas != -1) {
            TextureUtil.deleteTexture(normalsAtlas);
            normalsAtlas = -1;
        }
        if (specularAtlas != -1) {
            TextureUtil.deleteTexture(specularAtlas);
            specularAtlas = -1;
        }
        normalsCount = 0;
        specularCount = 0;
    }

    // Creates an atlas texture with its mip levels.
    private static int allocateAtlas(int width, int height, int mipmapLevels, int fillArgb) {
        int texture = TextureUtil.glGenTextures();
        TextureUtil.allocateTextureImpl(texture, mipmapLevels, width, height);

        GlStateManager.bindTexture(texture);
        TextureUtil.uploadTextureMipmap(neutralMipLevels(width, height, mipmapLevels, fillArgb),
                width, height, 0, 0, false, false);
        return texture;
    }

    // Applies the pbr sampling.
    private static void applyPbrSampling(int mipmapLevels) {
        GlStateManager.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        GlStateManager.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER,
                mipmapLevels > 0 ? GL_NEAREST_MIPMAP_NEAREST : GL_NEAREST);
        GlStateManager.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        GlStateManager.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    }

    // Returns neutral data for every mip level.
    private static int[][] neutralMipLevels(int width, int height, int mipmapLevels, int fillArgb) {
        int levelCount = Math.max(1, mipmapLevels + 1);
        int[][] levels = new int[levelCount][];
        for (int level = 0; level < levelCount; level++) {
            int levelWidth = Math.max(1, width >> level);
            int levelHeight = Math.max(1, height >> level);
            int[] fill = new int[levelWidth * levelHeight];
            java.util.Arrays.fill(fill, fillArgb);
            levels[level] = fill;
        }
        return levels;
    }

    // Uploads the normal or specular companion of a sprite and returns whether it existed.
    private static boolean uploadCompanion(TextureAtlasSprite sprite, String suffix, int atlasTexture, int mipmapLevels) {
        BufferedImage image = readCompanion(sprite.getIconName(), suffix);
        if (image == null) {
            return false;
        }

        int iconWidth = sprite.getIconWidth();
        int iconHeight = sprite.getIconHeight();

        int[] pixels = extractFrame(image, iconWidth, iconHeight);
        if (pixels == null) {
            return false;
        }

        int[][] levels;
        try {
            levels = mipmapLevels > 0
                    ? TextureUtil.generateMipmapData(mipmapLevels, iconWidth, new int[][] { pixels })
                    : new int[][] { pixels };
        } catch (Exception e) {
            levels = new int[][] { pixels };
        }

        GlStateManager.bindTexture(atlasTexture);
        TextureUtil.uploadTextureMipmap(levels, iconWidth, iconHeight, sprite.getOriginX(), sprite.getOriginY(), false, false);
        return true;
    }

    // Returns the pixels of one animation frame.
    private static int[] extractFrame(BufferedImage image, int iconWidth, int iconHeight) {
        int srcWidth = image.getWidth();
        int srcHeight = image.getHeight();
        if (srcWidth <= 0 || srcHeight <= 0) {
            return null;
        }

        int[] out = new int[iconWidth * iconHeight];
        for (int y = 0; y < iconHeight; y++) {
            int srcY = y * srcWidth / iconWidth;
            if (srcY >= srcHeight) {
                srcY = srcHeight - 1;
            }
            for (int x = 0; x < iconWidth; x++) {
                int srcX = x * srcWidth / iconWidth;
                out[y * iconWidth + x] = image.getRGB(srcX, srcY);
            }
        }
        return out;
    }

    // Reads the companion image of a sprite or returns null.
    private static BufferedImage readCompanion(String iconName, String suffix) {
        ResourceLocation icon = new ResourceLocation(iconName);
        ResourceLocation location = new ResourceLocation(icon.getNamespace(),
                "textures/" + icon.getPath() + suffix + ".png");

        try (IResource resource = Minecraft.getMinecraft().getResourceManager().getResource(location)) {
            return ImageIO.read(resource.getInputStream());
        } catch (IOException e) {
            return null;
        }
    }
}
