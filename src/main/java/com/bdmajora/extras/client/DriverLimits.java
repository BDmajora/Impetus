package com.bdmajora.extras.client;

import com.bdmajora.extras.Extras;
import org.lwjgl.opengl.GL11;

// Limits reported by the graphics driver.
public final class DriverLimits {
    // Cached largest texture size or minus one.
    private static int maxTextureSize = -1;

    // Prevents instantiation
    private DriverLimits() {
    }

    // Returns the largest texture size or minus one when the limit is off.
    public static int maxTextureSize() {
        if (!Extras.options().loading.driverAtlasLimit) {
            return -1;
        }
        if (maxTextureSize <= 0) {
            maxTextureSize = GL11.glGetInteger(GL11.GL_MAX_TEXTURE_SIZE);
        }
        return maxTextureSize > 0 ? maxTextureSize : -1;
    }
}
