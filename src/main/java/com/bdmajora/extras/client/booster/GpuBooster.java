package com.bdmajora.extras.client.booster;

import com.bdmajora.extras.ExtrasConfig;

// Switches of the graphics speed features.
public final class GpuBooster {
    // Prevents instantiation
    private GpuBooster() {
    }

    // Applies the settings to the feature switches.
    public static void apply(ExtrasConfig.GpuBoosterSettings settings) {
        boolean on = settings.enabled;
        FastMath.enabled = on && settings.fastMath;
        FastRandom.enabled = on && settings.fastRandom;
        StreamingUploader.enabled = on && settings.streamUploads;
        StreamingUploader.formatCache = on && settings.streamUploads && settings.vertexFormatCache;
    }
}
