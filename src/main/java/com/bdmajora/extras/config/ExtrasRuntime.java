package com.bdmajora.extras.config;

import com.bdmajora.extras.ExtrasConfig;
import com.bdmajora.extras.async.ParallelProcessor;
import com.bdmajora.extras.client.ThreadTuning;
import com.bdmajora.extras.client.bakedentities.BakedEntities;
import com.bdmajora.extras.client.booster.GpuBooster;
import com.bdmajora.extras.client.particle.ParticleTicker;

// Runtime switches derived from the Extras config.
public final class ExtrasRuntime {
    // Prevents instantiation
    private ExtrasRuntime() {
    }

    // Installs the value.
    public static void install(ExtrasConfig options) {
        ParallelProcessor.install(options.async);
    }

    // Applies the value.
    public static void apply(ExtrasConfig options) {
        GpuBooster.apply(options.gpuBooster);
        ParallelProcessor.apply(options.async);
        ParticleTicker.apply(options.particle);
        BakedEntities.apply(options.bakedEntities);
        ThreadTuning.apply(options.threads);
    }
}
