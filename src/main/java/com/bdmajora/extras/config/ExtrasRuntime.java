package com.bdmajora.extras.config;

import com.bdmajora.extras.ExtrasConfig;
import com.bdmajora.extras.async.ParallelProcessor;
import com.bdmajora.extras.client.ThreadTuning;
import com.bdmajora.extras.client.bakedentities.BakedEntities;
import com.bdmajora.extras.client.booster.GpuBooster;
import com.bdmajora.extras.client.particle.ParticleTicker;

// Copies the settings of a config into the subsystems that hold them in static state
public final class ExtrasRuntime {
    // Prevents instantiation
    private ExtrasRuntime() {
    }

    // Installs the parallel ticking master switch that holds until the next launch
    public static void install(ExtrasConfig options) {
        ParallelProcessor.install(options.async);
    }

    // Applies every setting that takes effect at once
    public static void apply(ExtrasConfig options) {
        GpuBooster.apply(options.gpuBooster);
        ParallelProcessor.apply(options.async);
        ParticleTicker.apply(options.particle);
        BakedEntities.apply(options.bakedEntities);
        ThreadTuning.apply(options.threads);
    }
}
