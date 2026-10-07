package com.bdmajora.extras.client.booster;

import com.bdmajora.extras.ExtrasConfig;

// The GPU Booster switches as plain statics, after GPUBooster by Mr.Toad (GPL-3, ported with permission): the hooks sit on hot paths (every angle wrap, every entity and particle constructor, every immediate-mode draw), so they read one static boolean rather than walking the config each call; applied whenever the Extras config loads or saves
public final class GpuBooster {
    private GpuBooster() {
    }

    // Master gates every sub-switch, so turning the group off restores vanilla behaviour in one step
    public static void apply(ExtrasConfig.GpuBoosterSettings settings) {
        boolean on = settings.enabled;
        FastMath.enabled = on && settings.fastMath;
        FastRandom.enabled = on && settings.fastRandom;
        StreamingUploader.enabled = on && settings.streamUploads;
        StreamingUploader.formatCache = on && settings.streamUploads && settings.vertexFormatCache;
    }
}
