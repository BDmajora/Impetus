package com.bdmajora.impetus.umbra.gl.image;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// Image unit limit of the driver.
public final class ImageLimits {
    // GL enum of the image unit limit.
    private static final int GL_MAX_IMAGE_UNITS = 0x8D57;

    // Cached limits or null.
    private static ImageLimits instance;

    // Number of image units.
    private final int maxImageUnits;

    // Creates an image limits with default state.
    private ImageLimits() {
        this.maxImageUnits = Math.max(0, LWJGL.glGetInteger(GL_MAX_IMAGE_UNITS));
    }

    // Returns the value.
    public static ImageLimits get() {
        if (instance == null) {
            instance = new ImageLimits();
        }
        return instance;
    }

    // Drops the cached limits.
    public static void reset() {
        instance = null;
    }

    // Returns the max image units.
    public int getMaxImageUnits() {
        return this.maxImageUnits;
    }
}
