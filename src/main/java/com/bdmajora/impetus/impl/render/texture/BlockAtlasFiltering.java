package com.bdmajora.impetus.impl.render.texture;

import com.bdmajora.impetus.engine.impl.ImpetusRuntimeOptions;
import com.bdmajora.impetus.engine.impl.gui.ImpetusGameOptions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureMap;
import org.lwjgl.opengl.EXTTextureFilterAnisotropic;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL;

// Applies the block atlas sampler state on rebuild and options apply; minification is pinned to NEAREST_MIPMAP_LINEAR since the atlas has no sprite borders and any wider filter bleeds neighbours, while magnification stays configurable
public final class BlockAtlasFiltering {
    private static Boolean anisotropySupported;

    private BlockAtlasFiltering() {
    }

    // Re-applies filtering to the currently loaded block atlas (safe to call on the client thread).
    public static void reapplyToBlockAtlas() {
        var mc = Minecraft.getMinecraft();
        if (mc == null) {
            return;
        }

        TextureMap atlas = mc.getTextureMapBlocks();
        if (atlas != null) {
            apply(atlas.getGlTextureId());
        }
    }

    // Sets the three parameters on the bound texture; they persist across binds
    public static void apply(int glTextureId) {
        if (glTextureId <= 0) {
            return;
        }

        boolean hasMipmaps = Minecraft.getMinecraft().gameSettings.mipmapLevels > 0;

        int minFilter = hasMipmaps ? GL11.GL_NEAREST_MIPMAP_LINEAR : GL11.GL_NEAREST;
        int magFilter = ImpetusRuntimeOptions.pixelFiltering == ImpetusGameOptions.PixelFilteringMode.LINEAR
                ? GL11.GL_LINEAR
                : GL11.GL_NEAREST;

        GL11.glBindTexture(GL11.GL_TEXTURE_2D, glTextureId);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, minFilter);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, magFilter);

        // Written every time: the parameter lives on the texture object, so a level set by a driver profile or another mod would otherwise survive and bleed the atlas
        if (isAnisotropySupported()) {
            GL11.glTexParameterf(GL11.GL_TEXTURE_2D,
                    EXTTextureFilterAnisotropic.GL_TEXTURE_MAX_ANISOTROPY_EXT, 1.0f);
        }
    }

    // Extension check, so the reset is skipped on drivers without it
    private static boolean isAnisotropySupported() {
        if (anisotropySupported == null) {
            try {
                anisotropySupported = GL.getCapabilities().GL_EXT_texture_filter_anisotropic;
            } catch (Throwable t) {
                anisotropySupported = false;
            }
        }

        return anisotropySupported;
    }
}
