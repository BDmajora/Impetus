package com.bdmajora.impetus.engine.impl.model.light;

import com.bdmajora.impetus.engine.impl.model.light.data.LightDataAccess;
import com.bdmajora.impetus.engine.impl.model.light.flat.FlatLightPipeline;
import com.bdmajora.impetus.engine.impl.model.light.smooth.SmoothLightPipeline;

// Holds the quad lighters; on Forge with the experimental pipeline a passthrough hands lighting to Forge's QuadLighter, otherwise the built-in Smooth/Flat pipelines (vanilla-like with optimisations and fixes) are used
public class LightPipelineProvider {
    private final LightPipeline smooth;
    private final LightPipeline flat;
    private final LightDataAccess lightData;

    public LightPipelineProvider(LightDataAccess cache, DiffuseProvider diffuseProvider, boolean useQuadNormalsForShading) {
        this.lightData = cache;
        this.smooth = new SmoothLightPipeline(cache, diffuseProvider, useQuadNormalsForShading);
        this.flat = new FlatLightPipeline(cache, diffuseProvider, useQuadNormalsForShading);
    }

    // Flat or smooth; asked per block, so a field pick rather than a map lookup
    public LightPipeline getLighter(LightMode type) {
        return type == LightMode.SMOOTH ? this.smooth : this.flat;
    }

    // The shared light cache both pipelines read
    public LightDataAccess getLightData() {
        return this.lightData;
    }

    // Resets the light pipelines and invalidates their caches; called whenever the underlying world data changes
    public void reset() {
        this.smooth.reset();
        this.flat.reset();
    }
}
