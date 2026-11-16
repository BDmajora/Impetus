package com.bdmajora.impetus.umbra.pipeline.shadow;

import com.bdmajora.impetus.umbra.gl.GlTextureUnits;
import com.bdmajora.impetus.umbra.gl.texture.InternalTextureFormat;
import com.bdmajora.impetus.umbra.targets.DepthTexture;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL33;

import java.nio.ByteBuffer;
import java.util.Objects;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// Depth and color textures of the shadow map.
public final class ShadowTextures {
    // Texture unit used while creating textures.
    public static final int SETUP_UNIT = 31;
    // Swizzle that spreads depth across the channels.
    private static final int[] DEPTH_SWIZZLE = {GL11.GL_RED, GL11.GL_RED, GL11.GL_RED, GL11.GL_ONE};

    // Prevents instantiation
    private ShadowTextures() {
    }

    // Returns a valid shadow resolution.
    public static int checkResolution(int resolution) {
        if (resolution <= 0) {
            throw new IllegalArgumentException("Shadow resolution must be positive: " + resolution);
        }
        return resolution;
    }

    // Creates a shadow depth texture.
    public static DepthTexture createDepth(int resolution, boolean hardwareFiltering, boolean mipmap, boolean nearest,
                                           boolean separateHardwareSamplers) {
        checkResolution(resolution);
        GlTextureUnits.selectScratch(SETUP_UNIT);
        try {
            DepthTexture texture = new DepthTexture(resolution, resolution,
                    GL14.GL_DEPTH_COMPONENT24, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT);
            try {
                LWJGL.glBindTexture(GL11.GL_TEXTURE_2D, texture.getTextureId());
                if (hardwareFiltering && !separateHardwareSamplers) {
                    LWJGL.glTexParameteri(GL11.GL_TEXTURE_2D, GL14.GL_TEXTURE_COMPARE_MODE, GL30.GL_COMPARE_REF_TO_TEXTURE);
                }
                LWJGL.glTexParameteriv(GL11.GL_TEXTURE_2D, GL33.GL_TEXTURE_SWIZZLE_RGBA, DEPTH_SWIZZLE);
                LWJGL.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
                LWJGL.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
                LWJGL.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, minFilter(mipmap, nearest));
                LWJGL.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, magFilter(nearest));
                LWJGL.glBindTexture(GL11.GL_TEXTURE_2D, 0);
            } catch (Throwable failure) {
                texture.destroy();
                throw failure;
            }
            return texture;
        } finally {
            GlTextureUnits.releaseScratch();
        }
    }

    // Creates a shadow color texture.
    public static int createColor(int resolution, InternalTextureFormat format, boolean mipmap, boolean nearest) {
        checkResolution(resolution);
        Objects.requireNonNull(format, "format");
        int texture = LWJGL.glGenTextures();
        GlTextureUnits.selectScratch(SETUP_UNIT);
        try {
            LWJGL.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            LWJGL.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, minFilter(mipmap, nearest));
            LWJGL.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, magFilter(nearest));
            LWJGL.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            LWJGL.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            LWJGL.glTexImage2D(GL11.GL_TEXTURE_2D, 0, format.getInternalFormat(), resolution, resolution, 0,
                    format.getPixelFormat(), format.getPixelType(), (ByteBuffer) null);
            LWJGL.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        } catch (Throwable failure) {
            LWJGL.glDeleteTextures(texture);
            throw failure;
        } finally {
            GlTextureUnits.releaseScratch();
        }
        return texture;
    }

    // Returns the minify filter of a target.
    public static int minFilter(boolean mipmap, boolean nearest) {
        if (mipmap) {
            return nearest ? GL11.GL_NEAREST_MIPMAP_NEAREST : GL11.GL_LINEAR_MIPMAP_LINEAR;
        }
        return nearest ? GL11.GL_NEAREST : GL11.GL_LINEAR;
    }

    // Returns the magnify filter of a target.
    public static int magFilter(boolean nearest) {
        return nearest ? GL11.GL_NEAREST : GL11.GL_LINEAR;
    }
}
