package com.bdmajora.extras.client;

import com.bdmajora.extras.Extras;
import org.lwjgl.opengl.GL11;

// Driver-reported limits the loading mixins substitute for vanilla's probing; each is read once from the context and kept
public final class DriverLimits {
    private static int maxTextureSize = -1;

    private DriverLimits() {
    }

    // GL_MAX_TEXTURE_SIZE, the driver's own word on the largest atlas, or -1 when the switch is off or the query answered nothing (the caller then falls through to vanilla's probe)
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
