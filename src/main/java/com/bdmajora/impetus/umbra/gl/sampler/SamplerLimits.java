package com.bdmajora.impetus.umbra.gl.sampler;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// Texture unit limits of the driver.
public final class SamplerLimits {
    // GL enum of the texture unit limit.
    private static final int GL_MAX_TEXTURE_IMAGE_UNITS = 0x8872;
    // GL enum of the draw buffer limit.
    private static final int GL_MAX_DRAW_BUFFERS = 0x8824;

    // Cached limits or null.
    private static SamplerLimits instance;

    // Number of texture units.
    private final int maxTextureUnits;
    // Number of draw buffers.
    private final int maxDrawBuffers;

    // Creates a sampler limits with default state.
    private SamplerLimits() {
        this.maxTextureUnits = LWJGL.glGetInteger(GL_MAX_TEXTURE_IMAGE_UNITS);
        this.maxDrawBuffers = LWJGL.glGetInteger(GL_MAX_DRAW_BUFFERS);
    }

    // Returns the value.
    public static SamplerLimits get() {
        if (instance == null) {
            instance = new SamplerLimits();
        }
        return instance;
    }

    // Drops the cached limits.
    public static void reset() {
        instance = null;
    }

    // Returns the max texture units.
    public int getMaxTextureUnits() {
        return this.maxTextureUnits;
    }

    // Returns the max draw buffers.
    public int getMaxDrawBuffers() {
        return this.maxDrawBuffers;
    }
}
